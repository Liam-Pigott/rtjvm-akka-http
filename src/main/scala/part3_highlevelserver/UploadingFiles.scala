package part3_highlevelserver

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import java.io.File
import scala.concurrent.Future
import scala.util.{Failure, Success}

object UploadingFiles extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val filesRoute = {
    (pathEndOrSingleSlash & get) {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          |<body>
          | <form action="http://localhost:8080/upload" method="post" enctype="multipart/form-data">
          |   <input type="file" name="myFile">
          |   <button type="submit">Upload</button>
          | </form>
          |</body>
          |</html>
          |""".stripMargin
      ))
    } ~
    (path("upload") & extractLog) { log =>
      // multipart/form-data
      entity(as[Multipart.FormData]) { formData =>
        val partsSource: Source[Multipart.FormData.BodyPart, Any] = formData.parts // chunks of file
        val filePartsSink: Sink[Multipart.FormData.BodyPart, Future[Done]] = // materialize to a future
          Sink.foreach[Multipart.FormData.BodyPart] { bodyPart =>
            if(bodyPart.name == "myFile") {
              val filename = "src/main/resources/download/" + bodyPart.filename.getOrElse("tempFile_" + System.currentTimeMillis())
              val file = new File(filename)

              log.info(s"Writing to file $filename")

              val fileContentsSource: Source[ByteString, _] = bodyPart.entity.dataBytes
              val fileContentsSink: Sink[ByteString, _]  = FileIO.toPath(file.toPath)

              fileContentsSource.runWith(fileContentsSink) // stream source contents into sink (file)
            }
          }

        val writeOperationFuture = partsSource.runWith(filePartsSink) // stream graph
        onComplete(writeOperationFuture) {
          case Success(_) => complete("File uploaded")
          case Failure(ex) => complete(s"File failed to upload: $ex")
        }
      }
    }
  }

  Http().bindAndHandle(filesRoute, "localhost", 8080)
}
