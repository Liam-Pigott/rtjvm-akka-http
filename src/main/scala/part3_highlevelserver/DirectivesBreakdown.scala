package part3_highlevelserver

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {

  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
    * Filtering directives
    */
  val simpleHttpMethodRoute =
    post {
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute =
    path("about") {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          |   Hello from the about page!
          | </body>
          |</html>
          |""".stripMargin
      ))
    }

  val complexPathRoute =
    path("api" / "myEndPoint") { // /api/myEndPoint
      complete(StatusCodes.OK)
    }

  val dontConfuse =
    path("api/myEndPoint") { // Url encodes the string - api%2FmyEndPoint
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash {
      complete(StatusCodes.OK)
    }

//  Http().bindAndHandle(dontConfuse, "localhost", 8080)

  /**
    * Extraction directives
    */
  val pathExtractionRoute =
    path("api" / "item" / IntNumber) { (itemNumber: Int) =>
      println(s"I've got a number in my path: $itemNumber")
      complete(StatusCodes.OK)
    }

  val pathMultiExtractionRoute =
    path("api" / "order" / IntNumber / IntNumber) { (id, inventory) =>
      println(s"I've got 2 numbers in my path: $id and $inventory")
      complete(StatusCodes.OK)
    }

  //Http().bindAndHandle(pathMultiExtractionRoute, "localhost", 8080)

  val queryParamExtractionRoute =
    path("api"/ "item") {
      // 'id - symbol - automatically held in special mem zone and work on reference equality vs string - better performance
      parameter('id.as[Int]) { itemId: Int =>
        println(s"I've extracted the ID as: $itemId")
        complete(StatusCodes.OK)
      }
    }

  val extractRequestRoute =
    path("controlEndpoint") {
      extractRequest { httpRequest: HttpRequest =>
        extractLog { log: LoggingAdapter =>
          log.info(s"I got the http request: $httpRequest")
          complete(StatusCodes.OK)
        }
      }
    }

//  Http().bindAndHandle(queryParamExtractionRoute, "localhost", 8080)

  /**
    * Composite directives
    */

  val simpleNestedRoute =
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndpoint") & extractRequest & extractLog) { (request, log) =>
      log.info(s"I got the http request: $request")
      complete(StatusCodes.OK)
    }

  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
      path("aboutUs") {
        complete(StatusCodes.OK)
      }

  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  val blogByIdRoute =
    path(IntNumber) { blogId: Int =>
      complete(StatusCodes.OK)
    }

  val blogByQueryParamRoute =
    parameter('postId.as[Int]) { blogPostId =>
      complete(StatusCodes.OK)
    }

  val combinedBlogByIdRoute =
    (path(IntNumber) | parameter('postId.as[Int])) { blogPostId: Int =>
      complete(StatusCodes.OK)
    }

  /**
    * Actionable directives
    */
  val completeOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported!")) // http 500
    }

  val rejectRoute =
    path("home") {
      reject
    } ~
      path("index") {
        completeOkRoute
      }

  val getOrPutPath =
    path("api" / "myEndpoint") {
      get {
        completeOkRoute
      } ~
        post {
          complete(StatusCodes.Forbidden)
        }
    }

  Http().bindAndHandle(getOrPutPath, "localhost", 8081)
}
