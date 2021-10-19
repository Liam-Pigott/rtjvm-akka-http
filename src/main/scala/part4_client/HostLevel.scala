package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import spray.json._

import java.util.UUID
import scala.util.{Failure, Success}

/*
  RequestLevel - low-volume, low-latency requests
  HostLevel - high volume low latency requests
  ConnectionLevel - long-lived requests
 */
object HostLevel extends App with PaymentJsonProtocol {

  implicit val system = ActorSystem("HostLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")

  Source(1 to 10)
    .map(i => (HttpRequest(), i))
    .via(poolFlow)
    .map {
      case (Success(response), value) =>
        // IMPORTANT - or will block connection the res wants to get through - leaky connections
        response.discardEntityBytes()
        s"Request $value has received response: $response"
      case (Failure(ex), value) =>
        s"Request $value has failed: $ex"
    }
//    .runWith(Sink.foreach[String](println))

  import PaymentSystemDomain._

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-bad-account"),
    CreditCard("4111-1111-1111-1111", "321", "tx-best-account"),
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvmstore-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    (
        HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentRequest.toJson.prettyPrint
        )
      ),
      UUID.randomUUID().toString
    )
  )

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach { // (Try[HttpResponse], String)
      case (Success(response@HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        println(s"The order ID $orderId was not allowed to proceed: $response")
      case (Success(response), orderId) =>
        println(s"The order ID $orderId was successful and returned the response: $response")
      case (Failure(ex), orderId) =>
        println(s"The order ID $orderId could not be completed: $ex")
    }

  /*
    HostLevel should be used for high volume low latency requests
    connection pooling means long lived requests can starve connection pool

    for one-off requests, use request-level API
    long-lived, use connection-level API
   */

}
