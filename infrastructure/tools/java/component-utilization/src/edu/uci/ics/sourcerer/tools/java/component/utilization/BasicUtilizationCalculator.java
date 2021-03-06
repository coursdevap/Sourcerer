/* 
 * Sourcerer: an infrastructure for large-scale source code analysis.
 * Copyright (C) by contributors. See CONTRIBUTORS.txt for full list.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package edu.uci.ics.sourcerer.tools.java.component.utilization;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import edu.uci.ics.sourcerer.tools.java.db.schema.ComponentMetricsTable;
import edu.uci.ics.sourcerer.tools.java.db.schema.ComponentRelationsTable;
import edu.uci.ics.sourcerer.tools.java.db.schema.ComponentsTable;
import edu.uci.ics.sourcerer.tools.java.db.schema.EntitiesTable;
import edu.uci.ics.sourcerer.tools.java.db.schema.ProjectsTable;
import edu.uci.ics.sourcerer.tools.java.db.schema.RelationsTable;
import edu.uci.ics.sourcerer.tools.java.model.types.Component;
import edu.uci.ics.sourcerer.tools.java.model.types.ComponentMetric;
import edu.uci.ics.sourcerer.tools.java.model.types.ComponentRelation;
import edu.uci.ics.sourcerer.tools.java.model.types.Project;
import edu.uci.ics.sourcerer.tools.java.model.types.Relation;
import edu.uci.ics.sourcerer.util.io.FileUtils;
import edu.uci.ics.sourcerer.util.io.logging.TaskProgressLogger;
import edu.uci.ics.sourcerer.utils.db.BatchInserter;
import edu.uci.ics.sourcerer.utils.db.DatabaseRunnable;
import edu.uci.ics.sourcerer.utils.db.sql.ConstantCondition;
import edu.uci.ics.sourcerer.utils.db.sql.Query;
import edu.uci.ics.sourcerer.utils.db.sql.SelectQuerier;
import edu.uci.ics.sourcerer.utils.db.sql.SelectQuery;
import edu.uci.ics.sourcerer.utils.db.sql.TypedQueryResult;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class BasicUtilizationCalculator extends DatabaseRunnable {
  private BasicUtilizationCalculator() {}
  
  public static void calculateComponentUtilization() {
    new BasicUtilizationCalculator().run();
  }
  
  @Override
  protected void action() {
    SelectQuerier<Component, Integer> getComponents = new SelectQuerier<Component, Integer>(exec) {
      @Override
      public Query initialize() {
        query = exec.createSelectQuery(ComponentsTable.TABLE);
        select = ComponentsTable.COMPONENT_ID;
        cond = ComponentsTable.TYPE.compareEquals();
        
        query.addSelect(select);
        query.andWhere(cond);
        return query;
      }
    };
    
    TaskProgressLogger task = TaskProgressLogger.get();
    
    task.start("Computing basic utilization metrics");
    
    task.start("Loading the crawled projects set");
    Set<Integer> crawledProjects = new HashSet<>();
    try (SelectQuery query = exec.createSelectQuery(ProjectsTable.TABLE)) {
      query.addSelect(ProjectsTable.PROJECT_ID);
      query.andWhere(ProjectsTable.PROJECT_TYPE.compareEquals(Project.CRAWLED));
      crawledProjects.addAll(query.select().toCollection(ProjectsTable.PROJECT_ID));
    }
    task.finish();
    
    BatchInserter inserter = exec.makeInFileInserter(FileUtils.getTempDir(), ComponentMetricsTable.TABLE);

    task.start("Computing metric for libraries", "libraries processed", 500);
    try (SelectQuery getJars = exec.createSelectQuery(ComponentRelationsTable.TABLE);
         SelectQuery getFiles = exec.createSelectQuery(RelationsTable.RHS_EID.compareEquals(EntitiesTable.ENTITY_ID))) {
      getJars.addSelect(ComponentRelationsTable.TARGET_ID);
      ConstantCondition<Integer> condLibraryID = ComponentRelationsTable.SOURCE_ID.compareEquals();
      getJars.andWhere(condLibraryID, ComponentRelationsTable.TYPE.compareEquals(ComponentRelation.LIBRARY_CONTAINS_JAR));
      
      ConstantCondition<Integer> condProjectID = EntitiesTable.PROJECT_ID.compareEquals();
      
      getFiles.addSelect(RelationsTable.FILE_ID, RelationsTable.PROJECT_ID);
      getFiles.andWhere(condProjectID, RelationsTable.RELATION_TYPE.compareIn(EnumSet.of(Relation.USES, Relation.CALLS)));
      
      // For each library
      for (Integer libraryID : getComponents.select(Component.LIBRARY)) {
        Set<Integer> projects = new HashSet<>();
        Set<Integer> files = new HashSet<>();
        int uses = 0;
        
        // Look up each jar file in that library
        condLibraryID.setValue(libraryID);
        for (Integer jarID : getJars.select().toIterable(ComponentRelationsTable.TARGET_ID)) {

          // Which files use this jar?
          condProjectID.setValue(jarID);
          try (TypedQueryResult result = getFiles.select()) {
            while (result.next()) {
              Integer projectID = result.getResult(RelationsTable.PROJECT_ID);
              Integer fileID = result.getResult(RelationsTable.FILE_ID);
              // Only accept crawled projects
              if (crawledProjects.contains(projectID)) {
                projects.add(projectID);
                files.add(fileID);
                uses++;
              }
            }
          }
        }
        
        // Add the utilization metric to the database
        inserter.addInsert(ComponentMetricsTable.createInsert(libraryID, ComponentMetric.PROJECTS_USING, projects.size()));
        inserter.addInsert(ComponentMetricsTable.createInsert(libraryID, ComponentMetric.FILES_USING, files.size()));
        inserter.addInsert(ComponentMetricsTable.createInsert(libraryID, ComponentMetric.USES, uses));
        task.progress();
      }
    }
    task.finish();
    
    task.start("Performing database insert");
    inserter.insert();
    task.finish();
    
    task.finish();
  }
}
