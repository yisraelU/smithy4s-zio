
# Smithy4s-Zio

- A few small libs based off the great [Smithy4s](https://disneystreaming.github.io/smithy4s/) to enable integration with ZIO ecosystem.

#### Keep in mind this is WIP
 
## notes

## Published Modules
  - ZIO Prelude 
    - Automatic derivation of the following Typeclasses for Smithy4s generated schemas 
      - Debug
      - Hash
      - Equals
  - ZIO Schema for automatic derivation of ZIO Schema for Smithy Models
  - ZIO Http
    - ZIO Http Client and Server implementations for the [`alloy#simpleRestJsonProtocol`](https://github.com/disneystreaming/alloy)
    - an opinionated rest-like protocol using json over http

## Usage of ZIO Http Smithy4s integration
  - see example client and server in `example` module
  - 
  
