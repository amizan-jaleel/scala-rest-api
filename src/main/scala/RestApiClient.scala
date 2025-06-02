package org.amizan

import com.twitter.finagle.Http
import org.amizan.RestApiClient._
import play.api.libs.json.{JsValue, Json}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.{Method, Request, Response, Status}

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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


  // POST request to submit the result
  def postResult(result: JsValue): Future[JsValue] = {
    val path = s"/candidateTest/v3/problem/result?userKey=$userKey"
    val httpRequest = Request(Method.Post, path)
    httpRequest.host = host
    httpRequest.contentType = "application/json"
    httpRequest.setContentString(result.toString())

    client(httpRequest).map { response =>
      if (response.status == Status.Ok || response.status == Status.Created) {
        Json.parse(response.contentString)
      } else {
        throw new RuntimeException(s"POST request failed with status ${response.status}: ${response.contentString}")
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

  case class ToPost(
    results: List[MaxCallsResult]
  )
  object ToPost {
    implicit val format = Json.format[ToPost]
  }

  def main(args: Array[String]): Unit = {
    val client = new RestApiClient()
    val allCalls = Await.result(client.getDataset())
    val callsByCustomer = allCalls.callRecords.groupBy(_.customerId)
    val callsByCustomerAndDate = callsByCustomer.map { case (customerId, calls) =>
      (customerId, calls.groupBy { call =>
        formatTimestampToUTCDate(call.startTimestamp)
      })
    }
    val maxConcurrentCallsByCustomerAndDate: List[MaxCallsResult] = callsByCustomerAndDate.flatMap { case (customerId, callsByDate) =>
      callsByDate.map { case (date, calls) =>
        val (timestamp, maxConcurrentCalls, activeCalls) = findMaxConcurrentCalls(calls)
        MaxCallsResult(customerId, date, maxConcurrentCalls, timestamp, activeCalls.map(_.callId))
      }
    }.toList

    val toPost = ToPost(maxConcurrentCallsByCustomerAndDate)

    val response = Await.result(client.postResult(Json.toJson(toPost)))
    println(response)
  }

  def formatTimestampToUTCDate(timestampMillis: Long): String = {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
      .withZone(ZoneOffset.UTC)
    formatter.format(instant)
  }

  def findMaxConcurrentCalls(calls: List[Call]): (Long, Int, List[Call]) = {
    case class Event(time: Long, delta: Int, call: Call)
    case class State(time: Long, count: Int, activeCalls: Set[Call])

    val events = calls.flatMap { call =>
      List(
        Event(call.startTimestamp, 1, call),
        Event(call.endTimestamp, -1, call)
      )
    }.sortBy(_.time)

    val states = events.scanLeft(State(0L, 0, Set.empty[Call])) { (state, event) =>
      val newActiveCalls = if (event.delta > 0) {
        state.activeCalls + event.call
      } else {
        state.activeCalls - event.call
      }

      State(
        time = event.time,
        count = state.count + event.delta,
        activeCalls = newActiveCalls
      )
    }.tail

    val peakState = states.maxBy(_.count)
    (peakState.time, peakState.count, peakState.activeCalls.toList)
  }
}
