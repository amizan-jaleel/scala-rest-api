package org.amizan

import com.twitter.finagle.Http
import org.amizan.RestApiClient._
import play.api.libs.json.{JsValue, Json}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.{Method, Request, Response, Status}

import java.util.UUID


class RestApiClient {
  private val userKey = "ea29b99a3d2c019338d547464070"
  private val host = "candidate.hubteam.com"

  private val client = Http.client
    .withTls(host)
    .newService(s"$host:443")

  def getDataset(): Future[PhoneCallRecords] = {
    val path = s"/candidateTest/v3/problem/dataset?userKey=$userKey"
    val httpRequest = Request(Method.Get, path)
    httpRequest.host = host

    client(httpRequest).map { response =>
      if (response.status == Status.Ok) {
        Json.parse(response.contentString).as[PhoneCallRecords](PhoneCallRecords.format)
      } else {
        throw new RuntimeException(s"GET request failed with status ${response.status}: ${response.contentString}")
      }
    }
  }
}

object RestApiClient {
  case class Call(
    customerId: Int,
    callId: UUID,
    startTimestamp: Long,
    endTimestamp: Long,
  )
  object Call {
    implicit val format = Json.format[Call]
  }
  case class PhoneCallRecords(
    callRecords: List[Call],
  )
  object PhoneCallRecords {
    implicit val format = Json.format[PhoneCallRecords]
  }

  case class MaxCallsResult(
    customerId: Int,
    date: String,
    maxConcurrentCalls: Int,
    timestamp: Long,
    callIds: List[UUID],
  )
  object MaxCallsResult {
    implicit val format = Json.format[MaxCallsResult]
  }

  def main(args: Array[String]): Unit = {
    val client = new RestApiClient()
    val r = Await.result(client.getDataset())
    println(Json.prettyPrint(Json.toJson(r)))
  }
}
