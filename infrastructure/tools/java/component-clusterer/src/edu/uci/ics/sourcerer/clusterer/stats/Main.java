package edu.uci.ics.sourcerer.clusterer.stats;

import static edu.uci.ics.sourcerer.db.util.DatabaseConnection.DATABASE_PASSWORD;
import static edu.uci.ics.sourcerer.db.util.DatabaseConnection.DATABASE_URL;
import static edu.uci.ics.sourcerer.db.util.DatabaseConnection.DATABASE_USER;
import static edu.uci.ics.sourcerer.util.io.Logging.logger;
import edu.uci.ics.sourcerer.clusterer.db.ClustererDatabaseAccessor;
import edu.uci.ics.sourcerer.db.util.DatabaseConnection;
import edu.uci.ics.sourcerer.util.io.Logging;
import edu.uci.ics.sourcerer.util.io.Property;
import edu.uci.ics.sourcerer.util.io.PropertyManager;
import edu.uci.ics.sourcerer.util.io.properties.BooleanProperty;

public class Main {
  public static Property<Boolean> GATHER_FQN_STATS = new BooleanProperty("gather-fqn-stats", false, "Clusterer", "Gather fqn statistics.");
  
  public static void main(String[] args) {
    PropertyManager.initializeProperties(args);
    Logging.initializeLogger();
    
    if (GATHER_FQN_STATS.getValue()) {
      PropertyManager.registerAndVerify(GATHER_FQN_STATS, DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
      DatabaseConnection conn = new DatabaseConnection();
      if (conn.open()) {
        FqnStats stats = new FqnStats(new ClustererDatabaseAccessor(conn));
        stats.gatherFqnStats();
        conn.close();
      }
    } else {
      logger.info("No action selected");
      PropertyManager.registerAndVerify(GATHER_FQN_STATS);
      PropertyManager.printUsage();
    }
  }
}
