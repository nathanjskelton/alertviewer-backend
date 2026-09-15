// Demo data for the alert timeline / gantt view.
//
// Inserts AlertManagerEntry documents that look like alerts ingested from an
// alertmanager called "demo": some firing right now, the rest firing
// intermittently across the last WINDOW_DAYS days (7 by default).
//
// "demo" is deliberately NOT one of the configured alertmanagers, so the
// ingest cycle's resolve pass (which only touches entries whose alertmanager
// it just polled) leaves these records alone.
//
// Driven by tools/seedDemoAlerts.sh, which supplies CLEAN / HISTORY_STATUS /
// WINDOW_DAYS.

const AM = "demo";
const NOW = Date.now();
const MINUTE = 60000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const clean = (typeof CLEAN !== "undefined") && CLEAN;
const historyStatus = (typeof HISTORY_STATUS !== "undefined") ? HISTORY_STATUS : "RESOLVED";
const windowDays = (typeof WINDOW_DAYS !== "undefined") ? Number(WINDOW_DAYS) : 7;
const WINDOW = windowDays * DAY;

const removed = db.AlertManagerEntry.deleteMany({ _id: /^demo-/ });
print("removed " + removed.deletedCount + " existing demo alerts");
if (clean) {
  print("clean only, nothing inserted");
  quit(0);
}

// Deterministic PRNG so re-seeding produces the same shape.
let seed = 20260904;
function rnd() {
  seed = (seed * 1664525 + 1013904223) % 4294967296;
  return seed / 4294967296;
}
function between(lo, hi) { return lo + rnd() * (hi - lo); }
function pick(list) { return list[Math.floor(rnd() * list.length)]; }

function pad(n) { return (n < 10 ? "0" : "") + n; }
function friendly(ms) {
  const d = new Date(ms);
  return d.getUTCFullYear() + "." + pad(d.getUTCMonth() + 1) + "." + pad(d.getUTCDate()) +
    " at " + pad(d.getUTCHours()) + ":" + pad(d.getUTCMinutes()) + ":" + pad(d.getUTCSeconds());
}

const docs = [];
function add(series, occurrence, startMs, endMs, status) {
  const id = "demo-" + series.key + "-" + occurrence;
  docs.push({
    _id: id,
    alertmanager: AM,
    notes: [{
      timestamp: new Date(startMs),
      friendlyTime: friendly(startMs),
      user: "System",
      message: "New alert imported from " + AM,
    }],
    status: status,
    regex: false,
    acked: false,
    flapping: false,
    friendlyStartTime: friendly(startMs),
    friendlyEndTime: friendly(endMs),
    alert: {
      labels: {
        severity: series.severity,
        instance: series.instance,
        alertname: series.name,
        gm_instance: series.environment,
        team: series.team,
        job: "demo",
        environment: series.environment,
      },
      annotations: { summary: series.summary },
      startsAt: new Date(startMs),
      endsAt: new Date(endMs),
      updatedAt: new Date(Math.min(endMs, NOW)),
      generatorURL: "http://demo:9090/graph?g0.expr=" + encodeURIComponent(series.key) + "&g0.tab=1",
      status: { state: status == "RESOLVED" ? "resolved" : "active" },
      receivers: ["bugle"],
      fingerprint: id,
    },
    lastChange: NumberLong(String(Math.min(endMs, NOW))),
    _class: "net.njsdomain.alertviewer.data.AlertManagerEntry",
  });
}

// --- Alerts that are firing right now -------------------------------------
// Staggered start times so the graph ramps up towards the right-hand edge and
// the gantt has bars of visibly different ages. The last few have been firing
// for days, which is what a stuck alert actually looks like.
const FIRING = [
  { key: "now-node-down", name: "NodeDown", severity: "critical", environment: "gmprod", team: "platform", instance: "worker07:9100", summary: "node_exporter on worker07 has been unreachable for 5m", agoMin: 3 },
  { key: "now-api-5xx", name: "ApiErrorRateHigh", severity: "critical", environment: "gmprod", team: "api", instance: "api-gw-02:8080", summary: "5xx rate on api-gw-02 is 12.4% (threshold 2%)", agoMin: 9 },
  { key: "now-kafka-lag", name: "KafkaConsumerLag", severity: "critical", environment: "gmprod", team: "streaming", instance: "kafka-03:9092", summary: "consumer group ingest-workers is 412k messages behind", agoMin: 21 },
  { key: "now-cert-expiring", name: "CertificateExpiring", severity: "warning", environment: "gmprod", team: "platform", instance: "ingress-01:443", summary: "TLS certificate for ingress-01 expires in 6 days", agoMin: 44 },
  { key: "now-disk-prod", name: "DiskSpaceLow", severity: "warning", environment: "gmprod", team: "storage", instance: "db-primary:9100", summary: "/var/lib/postgresql is 91% full", agoMin: 78 },
  { key: "now-mem-pressure", name: "MemoryPressure", severity: "warning", environment: "gmtest", team: "platform", instance: "worker12:9100", summary: "worker12 memory usage sustained above 92%", agoMin: 137 },
  { key: "now-latency", name: "HighRequestLatency", severity: "warning", environment: "gmtest", team: "api", instance: "search-svc:8080", summary: "p99 latency on search-svc is 3.2s (threshold 1s)", agoMin: 194 },
  { key: "now-backup", name: "BackupFailed", severity: "warning", environment: "gmdev", team: "storage", instance: "backup-runner:9100", summary: "nightly backup job exited non-zero", agoMin: 305 },
  { key: "now-replica-lag", name: "ReplicaLag", severity: "info", environment: "gmtest", team: "storage", instance: "db-replica-02:9187", summary: "replica is 42s behind primary", agoMin: 431 },
  { key: "now-queue-depth", name: "QueueDepthGrowing", severity: "info", environment: "gmdev", team: "streaming", instance: "rabbit-01:15692", summary: "work queue depth has grown steadily for 30m", agoMin: 592 },
  { key: "now-scrape-fail", name: "ScrapeFailed", severity: "info", environment: "gmdev", team: "platform", instance: "cadvisor-04:8080", summary: "prometheus cannot scrape cadvisor-04", agoMin: 745 },
  { key: "now-flap-route", name: "RouteFlapping", severity: "info", environment: "gmdev", team: "network", instance: "edge-rtr-01:161", summary: "BGP session with upstream has reset 4 times", agoMin: 1010 },
  { key: "now-old-ticket", name: "StaleIncident", severity: "info", environment: "gmdev", team: "ops", instance: "ops-board:8080", summary: "incident INC-4471 has been open for over 20h", agoMin: 1315 },
  // Firing continuously for days -- these give the 7d gantt full-width bars.
  { key: "now-stuck-pvc", name: "PvcPendingBind", severity: "warning", environment: "gmtest", team: "storage", instance: "worker05:10250", summary: "PVC analytics-scratch has been Pending for 2 days", agoMin: 2 * 24 * 60 + 190 },
  { key: "now-quota", name: "QuotaExhausted", severity: "warning", environment: "gmdev", team: "ops", instance: "quota-svc:8080", summary: "namespace sandbox is at 100% of its CPU quota", agoMin: 4 * 24 * 60 + 55 },
  { key: "now-eol-agent", name: "AgentVersionEol", severity: "info", environment: "gmdev", team: "platform", instance: "fleet-mgr:8080", summary: "17 hosts still run an end-of-life monitoring agent", agoMin: 6 * 24 * 60 + 410 },
];
FIRING.forEach(series => {
  const start = NOW - series.agoMin * MINUTE;
  // A live alert always carries an endsAt in the near future; alertmanager
  // keeps pushing it out while the alert is firing.
  add(series, 1, start, NOW + 5 * MINUTE, "NEW");
});

// --- Alerts that fired intermittently over the window ---------------------
// Each series repeats on its own cadence with a phase offset, so occurrences
// stagger across the week instead of lining up. Cadences are in hours: over a
// 7 day window a 10-40h cadence gives each series a handful of episodes rather
// than the several hundred a minutes-scale cadence would produce.
const INTERMITTENT = [
  { key: "int-disk-churn", name: "DiskSpaceLow", severity: "warning", environment: "gmtest", team: "storage", instance: "build-cache:9100", summary: "/var/cache crosses 85% during builds", phaseHr: 2, cadenceHr: 11, durMin: 95 },
  { key: "int-oom", name: "PodOOMKilled", severity: "critical", environment: "gmprod", team: "platform", instance: "worker03:10250", summary: "container analytics-worker was OOMKilled", phaseHr: 7, cadenceHr: 19, durMin: 25 },
  { key: "int-latency", name: "HighRequestLatency", severity: "warning", environment: "gmprod", team: "api", instance: "checkout-svc:8080", summary: "p99 latency on checkout-svc above 1s", phaseHr: 1, cadenceHr: 9, durMin: 140 },
  { key: "int-scrape", name: "ScrapeFailed", severity: "info", environment: "gmdev", team: "platform", instance: "node-exporter-09:9100", summary: "scrape of node-exporter-09 timed out", phaseHr: 4, cadenceHr: 14, durMin: 18 },
  { key: "int-cron", name: "CronJobFailed", severity: "warning", environment: "gmtest", team: "ops", instance: "cron-runner:9100", summary: "hourly reconcile job failed", phaseHr: 9, cadenceHr: 24, durMin: 12 },
  { key: "int-tls-handshake", name: "TlsHandshakeErrors", severity: "info", environment: "gmtest", team: "network", instance: "ingress-02:443", summary: "elevated TLS handshake failures from one peer", phaseHr: 16, cadenceHr: 31, durMin: 175 },
  { key: "int-replica", name: "ReplicaLag", severity: "warning", environment: "gmprod", team: "storage", instance: "db-replica-01:9187", summary: "replica falls behind during batch writes", phaseHr: 5, cadenceHr: 13, durMin: 210 },
  { key: "int-queue", name: "QueueDepthGrowing", severity: "info", environment: "gmdev", team: "streaming", instance: "rabbit-02:15692", summary: "queue depth spike while consumers restart", phaseHr: 12, cadenceHr: 17, durMin: 45 },
  { key: "int-cpu", name: "CpuThrottling", severity: "warning", environment: "gmtest", team: "platform", instance: "worker15:10250", summary: "cgroup cpu throttling above 40%", phaseHr: 20, cadenceHr: 21, durMin: 160 },
  { key: "int-dns", name: "DnsResolutionErrors", severity: "critical", environment: "gmprod", team: "network", instance: "coredns-02:9153", summary: "coredns-02 SERVFAIL rate above threshold", phaseHr: 26, cadenceHr: 37, durMin: 30 },
  { key: "int-etcd", name: "EtcdLeaderChange", severity: "critical", environment: "gmprod", team: "platform", instance: "etcd-02:2379", summary: "etcd cluster elected a new leader", phaseHr: 14, cadenceHr: 27, durMin: 15 },
  { key: "int-cert-renew", name: "CertificateExpiring", severity: "warning", environment: "gmtest", team: "platform", instance: "ingress-03:443", summary: "cert-manager renewal is overdue", phaseHr: 3, cadenceHr: 33, durMin: 330 },
  { key: "int-thermal", name: "HardwareThermal", severity: "warning", environment: "gmprod", team: "platform", instance: "rack4-bmc:623", summary: "inlet temperature above 32C in rack 4", phaseHr: 22, cadenceHr: 23, durMin: 250 },
  { key: "int-nfs", name: "NfsStale", severity: "warning", environment: "gmtest", team: "storage", instance: "worker09:9100", summary: "stale NFS handle on /mnt/shared", phaseHr: 11, cadenceHr: 16, durMin: 70 },
  { key: "int-webhook", name: "WebhookDeliveryFailed", severity: "info", environment: "gmdev", team: "api", instance: "hooks-svc:8080", summary: "outbound webhook retries exhausted", phaseHr: 2, cadenceHr: 10, durMin: 22 },
  { key: "int-license", name: "LicenseExpiring", severity: "info", environment: "gmtest", team: "ops", instance: "license-svc:8080", summary: "vendor license expires in under 30 days", phaseHr: 30, cadenceHr: 40, durMin: 400 },
  { key: "int-batch", name: "BatchJobSlow", severity: "info", environment: "gmdev", team: "streaming", instance: "spark-driver:4040", summary: "nightly aggregation running past its window", phaseHr: 8, cadenceHr: 24, durMin: 115 },
  { key: "int-conntrack", name: "ConntrackTableFull", severity: "warning", environment: "gmprod", team: "network", instance: "edge-lb-02:9100", summary: "conntrack table above 90% capacity", phaseHr: 6, cadenceHr: 12, durMin: 55 },
];
INTERMITTENT.forEach(series => {
  let occurrence = 0;
  let cursor = NOW - WINDOW + series.phaseHr * HOUR;
  while (cursor < NOW - 10 * MINUTE) {
    const duration = Math.round(between(series.durMin * 0.6, series.durMin * 1.4)) * MINUTE;
    const end = Math.min(cursor + duration, NOW - 2 * MINUTE);
    add(series, ++occurrence, cursor, end, historyStatus);
    cursor = end + Math.round(between(series.cadenceHr * 0.7, series.cadenceHr * 1.3) * 60) * MINUTE;
  }
});

// --- Long-running incidents -----------------------------------------------
// A handful of alerts that stayed up for days before being resolved, so the
// week has bars that span most of the gantt rather than only short blips.
const LONG = [
  { key: "long-dc-migration", name: "ReplicationDegraded", severity: "critical", environment: "gmprod", team: "storage", instance: "san-array-02:9100", summary: "cross-site replication degraded during DC migration", startDayAgo: 6.6, durDays: 3.1 },
  { key: "long-vendor-outage", name: "UpstreamProviderDown", severity: "critical", environment: "gmprod", team: "network", instance: "transit-b:161", summary: "secondary transit provider has been down since Monday", startDayAgo: 5.4, durDays: 2.2 },
  { key: "long-capacity", name: "ClusterCapacityLow", severity: "warning", environment: "gmtest", team: "platform", instance: "sched-01:10259", summary: "cluster cannot schedule new pods, awaiting hardware", startDayAgo: 6.9, durDays: 4.5 },
  { key: "long-cert-backlog", name: "CertificateExpiring", severity: "warning", environment: "gmdev", team: "platform", instance: "ingress-dev:443", summary: "dev wildcard cert expired, renewal blocked on approval", startDayAgo: 4.2, durDays: 1.8 },
  { key: "long-index-rebuild", name: "SearchIndexStale", severity: "info", environment: "gmtest", team: "api", instance: "search-01:9200", summary: "search index rebuild running since the weekend", startDayAgo: 3.3, durDays: 2.6 },
  { key: "long-audit-lag", name: "AuditPipelineLag", severity: "info", environment: "gmprod", team: "ops", instance: "audit-collector:8080", summary: "audit log shipping is behind by more than a day", startDayAgo: 2.1, durDays: 1.4 },
];
LONG.forEach(series => {
  const start = NOW - Math.round(series.startDayAgo * 24 * 60) * MINUTE;
  const end = Math.min(start + Math.round(series.durDays * 24 * 60) * MINUTE, NOW - 30 * MINUTE);
  add(series, 1, start, end, historyStatus);
});

// --- Incident storms ------------------------------------------------------
// Bursts of correlated alerts so the graph has obvious critical spikes to
// scrub into, spread across the week rather than bunched in one day.
const STORM_HOSTS = ["worker01", "worker02", "worker04", "worker05", "worker06", "worker08", "worker09", "worker11",
  "worker13", "worker14", "worker16", "worker18", "db-primary", "db-replica-01", "db-replica-02", "api-gw-01",
  "api-gw-03", "api-gw-04", "cache-01", "cache-02", "cache-03", "queue-01", "queue-02", "search-01"];
[
  { key: "storm-a", agoMin: 6 * 24 * 60 + 130, name: "NodeDown", severity: "critical", environment: "gmprod", team: "platform", summary: "host stopped reporting during rack power event", spreadMin: 26, durMin: 38 },
  { key: "storm-b", agoMin: 4 * 24 * 60 + 400, name: "NetworkPartition", severity: "warning", environment: "gmtest", team: "network", summary: "packet loss to the secondary switch fabric", spreadMin: 34, durMin: 52 },
  { key: "storm-c", agoMin: 2 * 24 * 60 + 260, name: "ApiErrorRateHigh", severity: "critical", environment: "gmprod", team: "api", summary: "5xx spike during bad deploy rollout", spreadMin: 18, durMin: 24 },
  { key: "storm-d", agoMin: 11 * 60, name: "DeploymentRolloutStuck", severity: "critical", environment: "gmprod", team: "api", summary: "rollout wedged, replicas unavailable across the fleet", spreadMin: 22, durMin: 31 },
].forEach(storm => {
  STORM_HOSTS.forEach((host, i) => {
    const start = NOW - storm.agoMin * MINUTE + Math.round(between(0, storm.spreadMin)) * MINUTE;
    const end = start + Math.round(between(storm.durMin * 0.5, storm.durMin)) * MINUTE;
    const series = {
      key: storm.key + "-" + host,
      name: storm.name,
      severity: i < Math.round(STORM_HOSTS.length * 0.65) ? storm.severity : "warning",
      environment: storm.environment,
      team: storm.team,
      instance: host + ":9100",
      summary: storm.summary,
    };
    add(series, 1, start, end, historyStatus);
  });
});

db.AlertManagerEntry.insertMany(docs);
print("inserted " + docs.length + " demo alerts over " + windowDays + "d (" + FIRING.length + " firing now, " +
  (docs.length - FIRING.length) + " historical as " + historyStatus + ")");
