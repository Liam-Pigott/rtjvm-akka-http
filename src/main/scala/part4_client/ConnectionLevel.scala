package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.util.{Failure, Success}
import spray.json._

/*
  RequestLevel - low-volume, low-latency requests
  HostLevel - high volume low latency requests
  ConnectionLevel - long-lived requests
 */
object ConnectionLevel extends App with PaymentJsonProtocol {

  implicit val system = ActorSystem("ConnectionLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val connectionFlow = Http().outgoingConnection("www.google.com")

  def oneOffRequest(request: HttpRequest) = {
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  oneOffRequest(HttpRequest()).onComplete {
    case Success(res) => println(s"Got successful response: $res")
    case Failure(ex) => println(s"Sending the request failed: $ex")
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
      uri = Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )

  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost", 8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()


}
