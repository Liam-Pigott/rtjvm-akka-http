package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import part3_highlevelserver.HighLevelExercise.Person
import spray.json._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personFormat = jsonFormat2(Person)
}

object HighLevelExercise extends App with PersonJsonProtocol {

  implicit val system = ActorSystem("HighLevelExample")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
    * GET /api/people: get all people registered
    * GET /api/people/pin: retrieve person with pin
    * GET /api/people?pin=X: same as above
    * POST /api/people with JSON payload for a person, add to db
    */

  case class Person(pin: Int, name: String)

  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie")
  )

  def getPeople: List[Person] = people
  def getPersonById(id: Int): Option[Person] = people.find(_.pin == id)

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val peopleRoute =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter('pin.as[Int])) { pin =>
          complete(getPersonById(pin).map(_.toJson.prettyPrint).map(toHttpEntity))
        } ~
          pathEndOrSingleSlash {
            complete(toHttpEntity(getPeople.toJson.prettyPrint))
          }
      } ~
        (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>
          val entity = request.entity
          val strictEntityFuture = entity.toStrict(2 seconds)
          val personFuture = strictEntityFuture.map(_.data.utf8String.parseJson.convertTo[Person])

          onComplete(personFuture) {
            case Success(person) =>
              log.info(s"Got person: $person")
              people = people :+ person
              complete(StatusCodes.OK)
            case Failure(ex) =>
              failWith(ex)
          }

//          personFuture.onComplete {
//            case Success(person) =>
//              log.info(s"Got person: $person")
//              people = people :+ person
//            case Failure(ex) =>
//              log.warning(s"Something failed with fetching person from entity")
//          }
//
//          complete(personFuture
//            .map(_ => StatusCodes.OK)
//            .recover {
//            case _ => StatusCodes.InternalServerError
//          })
        }
    }

  Http().bindAndHandle(peopleRoute, "localhost", 8080)
}
