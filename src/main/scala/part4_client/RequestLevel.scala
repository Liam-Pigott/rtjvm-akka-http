package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

import scala.util.{Failure, Success}
import spray.json._

/*
  RequestLevel - low-volume, low-latency requests
  HostLevel - high volume low latency requests
  ConnectionLevel - long-lived requests
 */
object RequestLevel extends App with PaymentJsonProtocol {

  implicit val system = ActorSystem("RequestLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "https://www.google.com"))

  responseFuture.onComplete {
    case Success(response) =>
      response.discardEntityBytes()
      println(s"The request was successful and returned: $response")
    case Failure(ex) =>
      println(s"The request failed with $ex")
  }

  import PaymentSystemDomain._

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-bad-account"),
    CreditCard("4111-1111-1111-1111", "321", "tx-best-account"),
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvmstore-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    HttpRequest(
      HttpMethods.POST,
      uri = "http://localhost:8080/api/payments",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )

  Source(serverHttpRequests)
    .mapAsync(10)(request => Http().singleRequest(request))
    .runForeach(println)
}
