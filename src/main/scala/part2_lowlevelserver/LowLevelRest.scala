package part2_lowlevelserver

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitarById(id: Int)
  case object FindAllGuitars

  case class AddQuantity(id: Int, quantity: Int)
  case class FindGuitarsInStock(inStock: Boolean)
}

class GuitarDB extends Actor with ActorLogging {

  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList
    case FindGuitarById(id) =>
      log.info(s"Searching for guitar by id: $id")
      sender() ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar $guitar with id: $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case AddQuantity(id, quantity) =>
      log.info(s"Attempting to add quantity $quantity for guitar $id")
      val guitar = guitars.get(id)
      val newGuitar = guitar.map {
        case Guitar(make, model, q) => Guitar(make, model, quantity + q)
      }
      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar
    case FindGuitarsInStock(inStock) =>
      log.info(s"Attempting to find guitars ${if(inStock) "in" else "out of"} stock")
      if(inStock)
        sender() ! guitars.values.filter(_.quantity > 0)
      else
        sender() ! guitars.values.filter(_.quantity <= 0)
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat3(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  import GuitarDB._

  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val simpleGuitar = Guitar("Fender", "Strat")
  println(simpleGuitar.toJson.prettyPrint)

  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Strat",
      |  "quantity":3
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  /*
  Setup
   */
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Strat"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach(guitarDb ! CreateGuitar(_))

  implicit val defaultTimeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt)

    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitarById(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val guitarId = query.get("id").map(_.toInt)
      val guitarQuantity = query.get("quantity").map(_.toInt)
      val validGuitarResponseFuture = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture = (guitarDb ? AddQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }
      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val inStockOption = query.get("inStock").map(_.toBoolean)

      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }


    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      /*
        Query params
       */
      val query = uri.query()
      if(query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else {
        // /api/guitar?id=42
        getGuitar(query)
      }
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entities are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds) // attempt to bring contents into memory over 3 seconds
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)
}
