

namespace smithy4s.hello

use alloy#simpleRestJson

@simpleRestJson
service HelloWorldService {
    version: "1.0.0",
    operations: [Hello,HealthCheck]
}

@http(method: "POST", uri: "/{name}", code: 200)
operation Hello {
    input: Person,
    output: Greeting
}

@http(method: "GET", uri: "/health", code: 200)
operation HealthCheck {
    output: Message
}

structure  Message {
    @required
    message: String
}

structure Person {
    @httpLabel
    @required
    name: String,

    @httpQuery("town")
    town: String
}

structure Greeting {
    @required
    message: String
}