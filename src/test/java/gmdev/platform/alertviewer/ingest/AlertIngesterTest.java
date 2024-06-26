package gmdev.platform.alertviewer.ingest;

import gmdev.platform.alertviewer.ingest.alertmananer.AlertIngester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class AlertIngesterTest {
    @InjectMocks
    AlertIngester alertIngester;

    @Test
    public void testIngest() {
        alertIngester.test();
    }

}
