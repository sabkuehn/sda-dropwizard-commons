package org.sdase.commons.client.jersey;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.sdase.commons.client.jersey.builder.PlatformClientBuilder;
import org.sdase.commons.client.jersey.error.ClientRequestExceptionMapper;
import org.sdase.commons.client.jersey.filter.ContainerRequestContextHolder;

import java.util.function.Function;

/**
 * A bundle that provides Jersey clients with appropriate configuration for the SDA Platform.
 */
public class JerseyClientBundle<C extends Configuration> implements ConfiguredBundle<C> {

   private ClientFactory clientFactory;

   private boolean initialized;

   private Function<C, String> consumerTokenProvider;

   public static InitialBuilder<Configuration> builder() {
      return new Builder<>();
   }

   private JerseyClientBundle(Function<C, String> consumerTokenProvider) {
      this.consumerTokenProvider = consumerTokenProvider;
   }

   @Override
   public void initialize(Bootstrap<?> bootstrap) {
      // no initialization needed here, we need the environment to initialize the client
   }

   @Override
   public void run(C configuration, Environment environment) {
      JerseyClientBuilder clientBuilder = new JerseyClientBuilder(environment).using(new JerseyClientConfiguration());
      this.clientFactory = new ClientFactory(clientBuilder, consumerTokenProvider.apply(configuration));
      environment.jersey().register(ContainerRequestContextHolder.class);
      environment.jersey().register(ClientRequestExceptionMapper.class);
      initialized = true;
   }

   /**
    * @return a factory to build clients that can be either used to call within the SDA platform or to call external
    *       services
    * @throws IllegalStateException if called before {@link io.dropwizard.Application#run(Configuration, Environment)}
    *       because the factory has to be initialized within {@link ConfiguredBundle#run(Object, Environment)}
    */
   public ClientFactory getClientFactory() {
      if (!initialized) {
         throw new IllegalStateException("Clients can be build in run(C, Environment), not in initialize(Bootstrap)");
      }
      return clientFactory;
   }


   //
   // Builder
   //

   public interface InitialBuilder<C extends Configuration> extends FinalBuilder<C> {
      /**
       * @param consumerTokenProvider A provider for the header value of the Http header
       *                              {@value org.sdase.commons.shared.tracing.ConsumerTracing#TOKEN_HEADER} that will
       *                              be send with each client request configured with
       *                              {@link PlatformClientBuilder#enableConsumerToken()}. If no such provider is
       *                              configured, {@link PlatformClientBuilder#enableConsumerToken()} will fail.
       * @return a builder instance for further configuration
       */
      <C1 extends Configuration> FinalBuilder<C1> withConsumerTokenProvider(ConsumerTokenProvider<C1> consumerTokenProvider);
   }

   public interface FinalBuilder<C extends Configuration> {
      JerseyClientBundle<C> build();
   }

   public static class Builder<C extends Configuration> implements InitialBuilder<C>, FinalBuilder<C> {

      private ConsumerTokenProvider<C> consumerTokenProvider = (C c) -> null;

      private Builder() {
      }

      private Builder(ConsumerTokenProvider<C> consumerTokenProvider) {
         this.consumerTokenProvider = consumerTokenProvider;
      }

      @Override
      public <C1 extends Configuration> FinalBuilder<C1> withConsumerTokenProvider(ConsumerTokenProvider<C1> consumerTokenProvider) {
         return new Builder<>(consumerTokenProvider);
      }

      @Override
      public JerseyClientBundle<C> build() {
         return new JerseyClientBundle<>(consumerTokenProvider);
      }
   }

   /**
    * Provides the consumer token that is added to outgoing requests from the configuration.
    *
    * @param <C> the type of the applications {@link Configuration} class
    */
   public interface ConsumerTokenProvider<C extends Configuration> extends Function<C, String> {}
}
