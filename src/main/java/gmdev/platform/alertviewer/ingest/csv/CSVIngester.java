package gmdev.platform.alertviewer.ingest.csv;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import gmdev.platform.alertviewer.ingest.EntryProcessor;
import gmdev.platform.alertviewer.ingest.IngestedEntry;
import gmdev.platform.alertviewer.ingest.Ingester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

@Component
@ConditionalOnProperty(value = "ingester.type", havingValue = "csv")
public class CSVIngester implements Ingester {
    private static final Logger log = LoggerFactory.getLogger(CSVIngester.class);

    @Autowired
    Environment env;

    @Autowired
    EntryProcessor processor;

    @Override
    public void ingest() {
        log.debug("CSVIngester scanning "+env.getProperty("files.path")+ File.separator+"index");

        File input = new File(env.getProperty("files.path")+File.separator+"index"+File.separator+"in");
        for (File file:input.listFiles()) {
            if (!file.getName().endsWith(".csv")) {
                log.debug("Skip non-index file: " + file.getName());
            }
            log.info("Found index file: " + file.getName());


            File proc = new File(env.getProperty("files.path")+File.separator+"index"+File.separator+"processing"+File.separator+file.getName());
            File done = new File(env.getProperty("files.path")+File.separator+"index"+File.separator+"done"+File.separator+file.getName());
            File error = new File(env.getProperty("files.path")+File.separator+"index"+File.separator+"error"+File.separator+file.getName());
            file.renameTo(proc);

            try {
                FileReader fileReader = new FileReader(proc);
                BufferedReader br = new BufferedReader(fileReader);

                CsvToBean<LogEntryCsvRow> csvToBean = new CsvToBeanBuilder<LogEntryCsvRow>(br)
                        .withType(LogEntryCsvRow.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .withSkipLines(1)
                        .build();
                List<LogEntryCsvRow> entries = csvToBean.parse();

                for (LogEntryCsvRow row:entries) {
                    if (row.getMessage() != null) {
                        processor.processIngestedEntry(new IngestedEntry(row.getTimestamp(), row.getLogType(), row.getId(), row.getMessage()));
                    }

                }

                proc.renameTo(done);
            } catch(Exception e) {
                log.error("Unable to read and save index file", e);
                proc.renameTo(error);
            }
        }
    }

}
