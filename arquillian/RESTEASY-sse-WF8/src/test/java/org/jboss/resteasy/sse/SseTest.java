package org.jboss.resteasy.sse;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventInput;
import javax.ws.rs.sse.SseEventSource.Listener;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.resteasy.plugins.providers.sse.SseEventSourceImpl;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class SseTest
{

   @ArquillianResource
   private URL baseURL;

   @Deployment(name = "resteasy-sse.war", order = 1)
   public static Archive<?> createTestArchive1()
   {
      WebArchive war1 = ShrinkWrap
            .create(WebArchive.class, "resteasy-sse.war")
            .setManifest(
                  new StringAsset("Manifest-Version: 1.0\n"
                        + "Dependencies: org.jboss.resteasy.resteasy-sse-provider\n")).addClasses(SseResource.class)
            .addClasses(SseApplication.class).addClasses(GreenHouse.class).addAsWebInfResource("web.xml")
            .addAsWebResource("index.html").addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
      return war1;
   }
   
   @Test
   public void testSingleSseEventInput() throws Exception {
      WebTarget target = ClientBuilder
         .newBuilder()
         .build()
         .target("http://localhost:8080" + baseURL.getPath() + "service/server-sent-events")
         .path("single");
      SseEventInput eventInput = target.request().get(SseEventInput.class);
      Assert.assertNotNull("SseEventInput is null", eventInput);
      if (!eventInput.isClosed()) {
         InboundSseEvent inboundEvent = eventInput.read();
         Assert.assertTrue(inboundEvent.getComment().indexOf("single event is added") > -1);
     }
   }

   @Test
   public void testEmptySseEventInput() throws Exception {
      WebTarget target = ClientBuilder
         .newBuilder()
         .build()
         .target("http://localhost:8080" + baseURL.getPath() + "service/server-sent-events");
      SseEventInput eventInput = target.request().get(SseEventInput.class);
      Assert.assertNotNull("SseEventInput is null", eventInput);   
   }
   @Test
   public void testAddMessage() throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(5);
      final List<String> results = new ArrayList<String>();
      WebTarget target = ClientBuilder.newBuilder().build()
            .target("http://localhost:8080" + baseURL.getPath() + "service/server-sent-events");

      SseEventSourceImpl.SourceBuilder builder = new SseEventSourceImpl.SourceBuilder(target);

      SseEventSourceImpl eventSource = (SseEventSourceImpl) builder.build();
      eventSource.register(new Listener()
      {
         @Override
         public void onEvent(InboundSseEvent event)
         {
            results.add(event.toString());
            latch.countDown();
         }
      });

      eventSource.open();

      WebTarget targetPost = ClientBuilder.newBuilder().build()
            .target("http://localhost:8080" + baseURL.getPath() + "service/server-sent-events");
      for (int counter = 0; counter < 5; counter++)
      {
         targetPost.request().post(Entity.text("message " + counter));
      }
       
      Assert.assertTrue("Waiting for evet to be delivered has timed out.", latch.await(10, TimeUnit.SECONDS));
      eventSource.close();
      Assert.assertTrue("5 messages are expected", results.size() == 5);
   }
   @Test
   public void testSseEvent() throws Exception
   {
      final List<String> results = new ArrayList<String>();
      final CountDownLatch latch = new CountDownLatch(6);
      WebTarget target = ClientBuilder.newBuilder().build()
            .target("http://localhost:8080" + baseURL.getPath() + "service/server-sent-events").path("domains")
            .path("1");
      SseEventSourceImpl.SourceBuilder builder = new SseEventSourceImpl.SourceBuilder(target);

      SseEventSourceImpl eventSource = (SseEventSourceImpl) builder.build();
      eventSource.register(new Listener()
      {
         @Override
         public void onEvent(InboundSseEvent event)
         {
            results.add(event.readData());
            latch.countDown();
         }
      });
      eventSource.open();
      target.request().buildPost(null);
      Assert.assertTrue("Waiting for evet to be delivered has timed out.", latch.await(10, TimeUnit.SECONDS));
      eventSource.close();
      Assert.assertTrue("6 SseInboundEvent expected", results.size() == 6);
      Assert.assertTrue("Expect the last event is Done event, but it is :" + results.toArray(new String[]
            {})[5], results.toArray(new String[]
      {})[5].indexOf("Done") > -1);
   }

   @Test
   //This will open a browser and test with html sse client
   public void testHtmlSse() throws Exception
   {
      
      Runtime runtime = Runtime.getRuntime();
      try
      {
         runtime.exec("xdg-open " + "http://localhost:8080" + baseURL.getPath());
      }
      catch (IOException e)
      {

      }
      Thread.sleep(30 * 1000);
   }
}
