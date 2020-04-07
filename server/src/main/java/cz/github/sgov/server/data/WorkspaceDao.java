package cz.github.sgov.server.data;

import com.google.gson.JsonObject;
import cz.github.sgov.server.Validator;
import cz.github.sgov.server.config.BackendProperties;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.topbraid.shacl.validation.ValidationReport;

@Slf4j
@Repository
public class WorkspaceDao {

  @Autowired
  BackendProperties properties;

  /**
   * Return all
   *
   * @return
   */
  @Autowired
  public List<String> getAllWorkspaceIris() {
    final String uri = properties.getRepositoryUrl();
    final HttpResponse<JsonObject> response =
        Unirest.post(uri).header("Content-type", "application/sparql-query")
            .header("Accept", "application/sparql-results+json")
            .body("SELECT ?iri WHERE { ?iri  a <" + Vocabulary.pracovniProstor + "> }")
            .asObject(JsonObject.class);

    final List<String> list = new ArrayList<>();
    response.getBody().getAsJsonObject("results").getAsJsonArray("bindings")
        .forEach(b -> list.add(
            ((JsonObject) b).getAsJsonObject("iri").getAsJsonPrimitive("value")
                .getAsString()));
    return list;
  }

  private List<String> getVocabularySnapshotContextsForWorkspace(final String workspace) {
    final String endpointUlozistePracovnichProstoru = properties.getRepositoryUrl();
    final QuerySolutionMap map = new QuerySolutionMap();
    map.add("workspace", ResourceFactory.createResource(workspace));
    map.add("odkazujeNaKontext", ResourceFactory.createResource(Vocabulary.odkazujeNaKontext));
    map.add("slovnikovyKontext", ResourceFactory.createResource(Vocabulary.slovnikovyKontext));
    final ParameterizedSparqlString query = new ParameterizedSparqlString(
        "SELECT ?kontext WHERE { ?workspace ?odkazujeNaKontext ?kontext . ?kontext a "
            + "?slovnikovyKontext }", map);
    final ResultSet rs =
        QueryExecutionFactory.sparqlService(endpointUlozistePracovnichProstoru, query.asQuery())
            .execSelect();

    final List<String> list = new ArrayList<>();
    while (rs.hasNext()) {
      list.add(rs.nextSolution().getResource("kontext").getURI());
    }
    return list;
  }

  /**
   * Validates workspace.
   *
   * @param workspaceIri workspace IRI
   * @return ValidationReport
   */
  public ValidationReport validateWorkspace(final String workspaceIri) {
    log.info("Validating workspace {}", workspaceIri);
    final String endpointUlozistePracovnichProstoru = properties.getRepositoryUrl();
    final List<String> vocabulariesForWorkspace =
        getVocabularySnapshotContextsForWorkspace(workspaceIri);
    log.info("- found vocabularies {}", vocabulariesForWorkspace);
    final String bindings = vocabulariesForWorkspace.stream().map(v -> "<" + v + ">")
        .collect(Collectors.joining(" "));
    final ParameterizedSparqlString query = new ParameterizedSparqlString(
        "CONSTRUCT {?s ?p ?o} WHERE  {GRAPH ?g {?s ?p ?o}} VALUES ?g {" + bindings + "}");
    log.info("- getting all statements for the vocabularies using query {}", query.toString());
    final QueryExecution e = QueryExecutionFactory
        .sparqlService(endpointUlozistePracovnichProstoru, query.asQuery());
    final Model m = e.execConstruct();
    log.info("- found {} statements. Now validating", m.listStatements().toSet().size());
    final Validator validator = new Validator();
    final Model shapesModel = validator.getRulesModel(Validator.getGlossaryRules());
    OntDocumentManager.getInstance().setProcessImports(false);
    final Model dataModel =
        ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF, m);
    final ValidationReport r = validator.validate(dataModel, shapesModel);
    log.info("- validated, with the following results:");
    r.results().forEach(result -> log.info(MessageFormat
        .format("    - [{0}] Node {1} failing for value {2} with message: {3} ",
            result.getSeverity().getLocalName(), result.getFocusNode(), result.getValue(),
            result.getMessage())));
    return r;
  }
}