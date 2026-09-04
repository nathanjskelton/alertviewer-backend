// Demo data for the 24h alert timeline / gantt view.
//
// Inserts AlertManagerEntry documents that look like alerts ingested from an
// alertmanager called "demo": some firing right now, the rest firing
// intermittently in a staggered pattern across the last 24 hours.
//
// "demo" is deliberately NOT one of the configured alertmanagers, so the
// ingest cycle's resolve pass (which only touches entries whose alertmanager
// it just polled) leaves these records alone.
//
// Driven by tools/seedDemoAlerts.sh, which supplies CLEAN / HISTORY_STATUS.

const AM = "demo";
const NOW = Date.now();
const MINUTE = 60000;
const HOUR = 60 * MINUTE;
const clean = (typeof CLEAN !== "undefined") && CLEAN;
const historyStatus = (typeof HISTORY_STATUS !== "undefined") ? HISTORY_STATUS : "RESOLVED";

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
// the gantt has bars of visibly different ages.
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
];
FIRING.forEach(series => {
  const start = NOW - series.agoMin * MINUTE;
  // A live alert always carries an endsAt in the near future; alertmanager
  // keeps pushing it out while the alert is firing.
  add(series, 1, start, NOW + 5 * MINUTE, "NEW");
});

// --- Alerts that fired intermittently over the last 24h --------------------
// Each series repeats on its own cadence with a phase offset, so occurrences
// stagger across the window instead of lining up.
const INTERMITTENT = [
  { key: "int-disk-churn", name: "DiskSpaceLow", severity: "warning", environment: "gmtest", team: "storage", instance: "build-cache:9100", summary: "/var/cache crosses 85% during builds", phaseMin: 25, cadenceMin: 95, durMin: 22 },
  { key: "int-oom", name: "PodOOMKilled", severity: "critical", environment: "gmprod", team: "platform", instance: "worker03:10250", summary: "container analytics-worker was OOMKilled", phaseMin: 70, cadenceMin: 145, durMin: 8 },
  { key: "int-latency", name: "HighRequestLatency", severity: "warning", environment: "gmprod", team: "api", instance: "checkout-svc:8080", summary: "p99 latency on checkout-svc above 1s", phaseMin: 15, cadenceMin: 65, durMin: 14 },
  { key: "int-scrape", name: "ScrapeFailed", severity: "info", environment: "gmdev", team: "platform", instance: "node-exporter-09:9100", summary: "scrape of node-exporter-09 timed out", phaseMin: 5, cadenceMin: 47, durMin: 6 },
  { key: "int-cron", name: "CronJobFailed", severity: "warning", environment: "gmtest", team: "ops", instance: "cron-runner:9100", summary: "hourly reconcile job failed", phaseMin: 40, cadenceMin: 60, durMin: 4 },
  { key: "int-tls-handshake", name: "TlsHandshakeErrors", severity: "info", environment: "gmtest", team: "network", instance: "ingress-02:443", summary: "elevated TLS handshake failures from one peer", phaseMin: 110, cadenceMin: 175, durMin: 18 },
  { key: "int-replica", name: "ReplicaLag", severity: "warning", environment: "gmprod", team: "storage", instance: "db-replica-01:9187", summary: "replica falls behind during batch writes", phaseMin: 55, cadenceMin: 122, durMin: 27 },
  { key: "int-queue", name: "QueueDepthGrowing", severity: "info", environment: "gmdev", team: "streaming", instance: "rabbit-02:15692", summary: "queue depth spike while consumers restart", phaseMin: 90, cadenceMin: 83, durMin: 11 },
  { key: "int-cpu", name: "CpuThrottling", severity: "warning", environment: "gmtest", team: "platform", instance: "worker15:10250", summary: "cgroup cpu throttling above 40%", phaseMin: 130, cadenceMin: 105, durMin: 16 },
  { key: "int-dns", name: "DnsResolutionErrors", severity: "critical", environment: "gmprod", team: "network", instance: "coredns-02:9153", summary: "coredns-02 SERVFAIL rate above threshold", phaseMin: 200, cadenceMin: 260, durMin: 9 },
  { key: "int-etcd", name: "EtcdLeaderChange", severity: "critical", environment: "gmprod", team: "platform", instance: "etcd-02:2379", summary: "etcd cluster elected a new leader", phaseMin: 145, cadenceMin: 190, durMin: 7 },
  { key: "int-cert-renew", name: "CertificateExpiring", severity: "warning", environment: "gmtest", team: "platform", instance: "ingress-03:443", summary: "cert-manager renewal is overdue", phaseMin: 35, cadenceMin: 138, durMin: 33 },
  { key: "int-thermal", name: "HardwareThermal", severity: "warning", environment: "gmprod", team: "platform", instance: "rack4-bmc:623", summary: "inlet temperature above 32C in rack 4", phaseMin: 210, cadenceMin: 168, durMin: 41 },
  { key: "int-nfs", name: "NfsStale", severity: "warning", environment: "gmtest", team: "storage", instance: "worker09:9100", summary: "stale NFS handle on /mnt/shared", phaseMin: 80, cadenceMin: 112, durMin: 19 },
  { key: "int-webhook", name: "WebhookDeliveryFailed", severity: "info", environment: "gmdev", team: "api", instance: "hooks-svc:8080", summary: "outbound webhook retries exhausted", phaseMin: 12, cadenceMin: 54, durMin: 9 },
  { key: "int-license", name: "LicenseExpiring", severity: "info", environment: "gmtest", team: "ops", instance: "license-svc:8080", summary: "vendor license expires in under 30 days", phaseMin: 165, cadenceMin: 205, durMin: 52 },
  { key: "int-batch", name: "BatchJobSlow", severity: "info", environment: "gmdev", team: "streaming", instance: "spark-driver:4040", summary: "nightly aggregation running past its window", phaseMin: 100, cadenceMin: 77, durMin: 24 },
  { key: "int-conntrack", name: "ConntrackTableFull", severity: "warning", environment: "gmprod", team: "network", instance: "edge-lb-02:9100", summary: "conntrack table above 90% capacity", phaseMin: 60, cadenceMin: 91, durMin: 13 },
];
INTERMITTENT.forEach(series => {
  let occurrence = 0;
  let cursor = NOW - 24 * HOUR + series.phaseMin * MINUTE;
  while (cursor < NOW - 10 * MINUTE) {
    const duration = Math.round(between(series.durMin * 0.6, series.durMin * 1.4)) * MINUTE;
    const end = Math.min(cursor + duration, NOW - 2 * MINUTE);
    add(series, ++occurrence, cursor, end, historyStatus);
    cursor = end + Math.round(between(series.cadenceMin * 0.7, series.cadenceMin * 1.3)) * MINUTE;
  }
});

// --- Two incident storms --------------------------------------------------
// Bursts of correlated alerts so the graph has obvious critical spikes to
// scrub into, ~17h and ~6h back.
const STORM_HOSTS = ["worker01", "worker02", "worker04", "worker05", "worker06", "worker08", "worker09", "worker11",
  "worker13", "worker14", "worker16", "worker18", "db-primary", "db-replica-01", "db-replica-02", "api-gw-01",
  "api-gw-03", "api-gw-04", "cache-01", "cache-02", "cache-03", "queue-01", "queue-02", "search-01"];
[
  { key: "storm-a", agoMin: 17 * 60, name: "NodeDown", severity: "critical", environment: "gmprod", team: "platform", summary: "host stopped reporting during rack power event", spreadMin: 26, durMin: 38 },
  { key: "storm-b", agoMin: 11 * 60, name: "NetworkPartition", severity: "warning", environment: "gmtest", team: "network", summary: "packet loss to the secondary switch fabric", spreadMin: 34, durMin: 52 },
  { key: "storm-c", agoMin: 6 * 60, name: "ApiErrorRateHigh", severity: "critical", environment: "gmprod", team: "api", summary: "5xx spike during bad deploy rollout", spreadMin: 18, durMin: 24 },
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
print("inserted " + docs.length + " demo alerts (" + FIRING.length + " firing now, " +
  (docs.length - FIRING.length) + " historical as " + historyStatus + ")");
