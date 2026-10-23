import com.sun.net.httpserver.HttpServer;
import server.ApiHandler;
import server.StaticHandler;
import service.AppService;
import service.DataStore;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Program entry point.  Usage:  java -cp out Main [port] [dataFolder]
 * 1. loads the data files   2. creates the service   3. starts the web server
 */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String dataDir = args.length > 1 ? args[1] : "data";

        DataStore store = new DataStore(dataDir);
        store.load();
        AppService service = new AppService(store);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(service));
        server.createContext("/", new StaticHandler("web"));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("=====================================================");
        System.out.println(" College Event & Volunteer Management System running");
        System.out.println(" Open in browser:  http://localhost:" + port);
        System.out.println(" Data folder:      " + dataDir + "/   (press Ctrl+C to stop)");
        System.out.println("=====================================================");
    }
}
