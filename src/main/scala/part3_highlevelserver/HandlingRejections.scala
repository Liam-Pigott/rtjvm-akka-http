package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.javadsl.server.MissingQueryParamRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, Rejection, RejectionHandler}

object HandlingRejections extends App {

  implicit val system = ActorSystem("HandlingRejections")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        parameter('id) { _ =>
          complete(StatusCodes.OK)
        }
    }

  /*
   rejections not failure - pass onto the rest of the routing tree
   rejections are aggregated
   */

  //Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }
  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler) {
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) {
              parameter('myParam) { _ =>
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }
  // RejectionHandler.default - top level for all by default - 404
//  Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8080)


  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    // use multiple handles in order of priority. a single handle would match the first rejection in the list
    .handle {
      case m: MethodRejection =>
        println(s"I got a method rejection: $m")
        complete("Rejected method")
    }
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a query param rejection: $m")
        complete("Rejected query param")
    }.result()

  Http().bindAndHandle(simpleRoute, "localhost", 8080)
}
