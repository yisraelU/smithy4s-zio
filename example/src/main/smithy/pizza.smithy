namespace examples

use smithy4s.api#simpleRestJson

@simpleRestJson
service PizzaAdminService {
    version: "1.0.0",
    errors: [GenericServerError, GenericClientError],
    operations: [AddMenuItem]
}

@http(method: "POST", uri: "/restaurant/{restaurant}/menu/item", code: 201)
operation AddMenuItem {
    input: AddMenuItemRequest,
    errors: [PriceError],
    output: AddMenuItemResult
}

structure AddMenuItemResult {
    @httpPayload
    @required
    itemId: String,
    @timestampFormat("epoch-seconds")
    @httpHeader("X-ADDED-AT")
    @required
    added: Timestamp
}

@error("client")
structure PriceError {
    @required
    message: String,
    @required
    @httpHeader("X-CODE")
    code: Integer
}

structure AddMenuItemRequest {
    @httpLabel
    @required
    restaurant: String,
    @httpPayload
    @required
    menuItem: MenuItem
}

structure MenuItem {
    @required
    food: Food,
    @required
    price: Float
}

union Food {
    pizza: Pizza,
    salad: Salad
}

structure Salad {
    @required
    name: String,
    @required
    ingredients: Ingredients
}

structure Pizza {
    @required
    name: String,
    @required
    base: PizzaBase,
    @required
    toppings: Ingredients
}

@enum([{name: "CREAM", value: "C"}, {name: "TOMATO", value: "T"}])
string PizzaBase

@enum([{value: "Mushroom"}, {value: "Cheese"}, {value: "Salad"}, {value: "Tomato"}])
string Ingredient

list Ingredients {
    member: Ingredient
}

@error("server")
@httpError(502)
structure GenericServerError {
    @required
    message: String
}

@error("client")
@httpError(418)
structure GenericClientError {
    @required
    message: String
}
