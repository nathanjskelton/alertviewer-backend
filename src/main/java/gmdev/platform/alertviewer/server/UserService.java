package gmdev.platform.alertviewer.server;

import gmdev.platform.alertviewer.data.AlertManagerUser;
import gmdev.platform.alertviewer.data.AlertManagerUserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    @Autowired MongoTemplate mongo;
    @Autowired Environment env;
    @Autowired AlertManagerUserRepo repo;
    @Autowired StateBuffer state;


    public UsersResponse getUsers() throws ServiceException {
        List<AlertManagerUser> users = repo.findAll();

        return new UsersResponse(users);
    }

    public void saveUser(AlertManagerUser user) {
        repo.save(user);
    }

    public void deleteUser(String id) {
        repo.deleteById(id);
    }
}
