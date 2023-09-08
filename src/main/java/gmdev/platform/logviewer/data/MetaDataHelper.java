package gmdev.platform.logviewer.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


@Component
public class MetaDataHelper {

    @Autowired MetaRepo repo;

    @Autowired
    Environment env;

    private MetaData get() {
        List<MetaData> md = repo.findAll();
        if (md == null || md.isEmpty()) {
            return null;
        }
        if (md.size() > 1) {
            throw new RuntimeException("There is more than one MetaData entity!!!!");
        }
        return md.get(0);
    }

    private void save(MetaData data) {
        repo.save(data);
    }

    public void setLastEnd(LocalDateTime dateTime) {
        MetaData md = get();
        if (md == null) {
            md = new MetaData();
        }
        md.setLastEndTime(dateTime);
        save(md);
    }

    public LocalDateTime getLastEnd() {
        MetaData md = get();
        if (md == null) {
            LocalDateTime ldt = LocalDateTime.now().minusDays(env.getProperty("initial.days.ago", Integer.class));
            md = new MetaData();
            md.setLastEndTime(ldt);
            save(md);
        }
        return md.getLastEndTime();
    }

}
