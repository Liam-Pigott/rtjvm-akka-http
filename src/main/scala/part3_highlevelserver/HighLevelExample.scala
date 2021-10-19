package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import spray.json._

object HighLevelExample extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("HighLevelExample")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  import GuitarDB._

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Strat"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach(guitarDb ! CreateGuitar(_))

  implicit val timeout = Timeout(2 seconds)
  val guitarServerRoute =
    path("api" / "guitar") {
      parameter('id.as[Int]) { guitarId: Int =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitarById(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      get {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        val entityFuture = guitarsFuture.map { guitars =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
      path("api" / "guitar" / IntNumber) { guitarId =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitarById(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
          parameter('inStock.as[Boolean]) { inStock =>
            val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
            val entityFuture = guitarsFuture.map { guitarList =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitarList.toJson.prettyPrint
              )
            }
            complete(entityFuture)
          }
        }
      }

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          complete((guitarDb ? FindGuitarsInStock(inStock))
            .mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity))
        }
      } ~
        (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
          complete((guitarDb ? FindGuitarById(guitarId))
            .mapTo[Option[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity))
        } ~
        pathEndOrSingleSlash {
          complete((guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity))
        }
    }

  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8080)
}
