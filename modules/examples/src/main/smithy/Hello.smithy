$version: "2.0"


namespace example.hello

use alloy#simpleRestJson

@simpleRestJson
service HelloWorldService {
    version: "1.0.0",
    operations: [Hello]
}

@http(method: "POST", uri: "/{name}", code: 200)
operation Hello {
    input: Person,
    output: Greeting
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
