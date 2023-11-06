$version: "2.0"

namespace example.todo

use alloy#simpleRestJson
use smithy4s.meta#unwrap

@simpleRestJson
service TodoService {
    version: "1.0"
    operations: [CreateTodo, GetTodo, UpdateTodo, DeleteTodo,DeleteAll ListTodos,ApiVersion,HealthCheck]
}

@http(method: "GET", uri: "/version")
operation ApiVersion {
    output: ApiVersionOutput
}

@http(method: "POST", uri: "/todo")
operation CreateTodo {
    input: CreateTodoInput
    output: Todo

}
@idempotent
@http(method: "DELETE", uri: "/todo")
operation  DeleteAll {
}

@http(method: "GET", uri: "/todo/{id}")
@readonly
operation GetTodo {
    input: GetTodoInput
    output: Todo
    errors: [TodoNotFound]
}

@http(method: "PATCH", uri: "/todo/{id}")
@idempotent
operation  UpdateTodo {
    input: UpdateTodoInput
    output: Todo
    errors: [TodoNotFound]
}

@idempotent
@http(method: "DELETE", uri: "/todo/{id}")
operation  DeleteTodo {
    input: DeleteTodoInput
    errors: [TodoNotFound]
}

@readonly
@http(method: "GET", uri: "/todo")
operation  ListTodos {
    output: ListTodosOutput

}

@http(method: "GET", uri: "/healthcheck")
operation HealthCheck {
    output: Status
}

structure Status {
    @required
    status: String
}

@documentation("outputs the git commit hash for the current app version")
structure ApiVersionOutput {
    @required
    version: String

}

string Id

string Title

integer Order

string TodoDescription

string Url

structure Todo {
    @required
    id: Id
    @required
    title: Title
    description: TodoDescription
    @required
    completed: Boolean
    order: Order,
    @required
    url: Url
}

structure CreateTodoInput {
    @required
    title: Title
    order:Order
    description: TodoDescription
}

structure GetTodoInput {
    @required
    @httpLabel
    id: Id
}


@error("client")
@httpError(404)
structure TodoNotFound {
    @required
    message: String
}

structure UpdateTodoInput {
    @required
    @httpLabel
    id: Id
    title: Title
    order: Order
    description: TodoDescription
    completed: Boolean
}

structure DeleteTodoInput {
    @httpLabel
    @required
    id: Id
}


list TodoList {
    member: Todo
}


structure ListTodosOutput {
    @required
    @httpPayload
    todos: TodoList
}