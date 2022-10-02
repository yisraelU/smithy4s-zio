/*
 *  Copyright 2021-2022 Disney Streaming
 *
 *  Licensed under the Tomorrow Open Source Technology License, Version 1.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     https://disneystreaming.github.io/TOST-1.0.txt
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package smithy4s.zio.http

import smithy4s.{Endpoint, Interpreter, Transformation}
import smithy4s.zio.http.internal.Smithy4sZHttpClientEndpoint
import zhttp.http.URL
import zhttp.service.Client
import zio.Task

// format: off
class Smithy4sZHttpReverseRouter[R,Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
                                                                                  baseUrl: URL,
                                                                                  service: smithy4s.Service[Alg, Op],
                                                                                  client: Client[R],
                                                                                  entityCompiler: EntityCompiler
                                                                               )
  extends Interpreter[Op, Task] {
  // format: on

  def apply[I, E, O, SI, SO](
      op: Op[I, E, O, SI, SO]
  ): Task[O] = {
    val (input, endpoint) = service.endpoint(op)
    val zhttpEndpoint = clientEndpoints(endpoint)
    zhttpEndpoint.send(input)
  }

  private val clientEndpoints =
    new Transformation[
      Endpoint[Op, *, *, *, *, *],
      Smithy4sZHttpClientEndpoint[Op, *, *, *, *, *]
    ] {
      def apply[I, E, O, SI, SO](
          endpoint: Endpoint[Op, I, E, O, SI, SO]
      ): Smithy4sZHttpClientEndpoint[Op, I, E, O, SI, SO] =
        Smithy4sZHttpClientEndpoint(
          baseUrl,
          client,
          endpoint,
          entityCompiler
        ).getOrElse(
          sys.error(
            s"Operation ${endpoint.name} is not bound to http semantics"
          )
        )
    }.precompute(service.endpoints.map(smithy4s.Kind5.existential(_)))
}
