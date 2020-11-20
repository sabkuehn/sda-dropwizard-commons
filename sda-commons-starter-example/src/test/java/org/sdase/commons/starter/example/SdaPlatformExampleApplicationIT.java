package org.sdase.commons.starter.example;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sdase.commons.server.opa.testing.OpaRule.onAnyRequest;

import io.dropwizard.testing.junit.DropwizardAppRule;
import io.openapitools.jackson.dataformat.hal.HALLink;
import java.util.List;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.server.auth.testing.AuthRule;
import org.sdase.commons.server.opa.testing.OpaRule;
import org.sdase.commons.shared.tracing.ConsumerTracing;
import org.sdase.commons.starter.SdaPlatformConfiguration;
import org.sdase.commons.starter.example.people.db.PersonEntity;
import org.sdase.commons.starter.example.people.db.TestDataUtil;
import org.sdase.commons.starter.example.people.rest.PersonResource;

public class SdaPlatformExampleApplicationIT {

  // create a dummy authentication provider that works as a local OpenId Connect provider for the
  // tests
  private static final AuthRule AUTH = AuthRule.builder().build();
  private static final OpaRule OPA = new OpaRule();

  private static final DropwizardAppRule<SdaPlatformConfiguration> DW =
      new DropwizardAppRule<>(
          SdaPlatformExampleApplication.class,
          // use the config file 'test-config.yaml' from the test resources folder
          resourceFilePath("test-config.yaml"),
          config("opa.baseUrl", OPA::getUrl));

  // apply the auth config to the test instance of the application
  // to verify incoming tokens correctly
  @ClassRule public static final RuleChain CHAIN = RuleChain.outerRule(AUTH).around(OPA).around(DW);

  private static final String TEST_CONSUMER_TOKEN = "test-consumer";

  @Before
  public void setupTestData() {
    TestDataUtil.clearTestData();
    PersonEntity john = TestDataUtil.addPersonEntity("john-doe", "John", "Doe");
    PersonEntity jane = TestDataUtil.addPersonEntity("jane-doe", "Jane", "Doe");
    TestDataUtil.addPersonEntity("jasmine-doe", "Jasmine", "Doe", asList(john, jane));

    OPA.reset();
  }

  @Test
  public void accessSwaggerWithoutAuthentication() {
    Response response = baseUrlWebTarget().path("openapi.json").request(APPLICATION_JSON).get();

    assertThat(response).extracting(Response::getStatus).isEqualTo(200);
  }

  @Test
  public void rejectApiRequestWithoutAuthentication() {
    Response response =
        baseUrlWebTarget()
            .path("people")
            .request(APPLICATION_JSON) // NOSONAR
            .header(ConsumerTracing.TOKEN_HEADER, TEST_CONSUMER_TOKEN)
            .get();

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  public void accessApiWithAuthenticationAndConsumerToken() {
    OPA.mock(onAnyRequest().allow());
    Response response =
        baseUrlWebTarget()
            .path("people")
            .request(APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, AUTH.auth().buildHeaderValue())
            .header(ConsumerTracing.TOKEN_HEADER, TEST_CONSUMER_TOKEN)
            .get();

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void respond404ForUnknownPerson() {
    OPA.mock(onAnyRequest().allow());
    Response response =
        baseUrlWebTarget()
            .path("people")
            .path("jamie-doe")
            .request(APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, AUTH.auth().buildHeaderValue())
            .header(ConsumerTracing.TOKEN_HEADER, TEST_CONSUMER_TOKEN)
            .get();

    assertThat(response).extracting(Response::getStatus).isEqualTo(404);
  }

  @Test
  public void provideSelfLinkInPersonResource() {
    OPA.mock(onAnyRequest().allow());
    PersonResource personResource =
        baseUrlWebTarget()
            .path("people")
            .path("john-doe")
            .request(APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, AUTH.auth().buildHeaderValue())
            .header(ConsumerTracing.TOKEN_HEADER, TEST_CONSUMER_TOKEN)
            .get(PersonResource.class);

    String expectedSelfUri = baseUrlWebTarget().getUri().toASCIIString() + "/people/john-doe";
    HALLink actualSelf = personResource.getSelfLink();
    assertThat(actualSelf).extracting(HALLink::getHref).isEqualTo(expectedSelfUri);
  }

  @Test
  public void provideRelationLinksInPersonResource() {
    OPA.mock(onAnyRequest().allow());
    PersonResource personResource =
        baseUrlWebTarget()
            .path("people")
            .path("jasmine-doe")
            .request(APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, AUTH.auth().buildHeaderValue())
            .header(ConsumerTracing.TOKEN_HEADER, TEST_CONSUMER_TOKEN)
            .get(PersonResource.class);

    String expectedJohnUri = baseUrlWebTarget().getUri().toASCIIString() + "/people/john-doe";
    String expectedJaneUri = baseUrlWebTarget().getUri().toASCIIString() + "/people/jane-doe";
    List<HALLink> actualParentsLinks = personResource.getParentsLinks();
    assertThat(actualParentsLinks)
        .extracting(HALLink::getHref)
        .containsExactlyInAnyOrder(expectedJohnUri, expectedJaneUri);
    List<HALLink> actualChildrenLinks = personResource.getChildrenLinks();
    assertThat(actualChildrenLinks).isNullOrEmpty();
  }

  private WebTarget baseUrlWebTarget() {
    return DW.client().target(String.format("http://localhost:%d", DW.getLocalPort()));
  }
}
