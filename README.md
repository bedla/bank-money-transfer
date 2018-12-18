# Spring Boot like money-transfer application without Spring

**Assignment:** Create REST application for sending money between accounts without full-blown frameworks like [Spring](https://spring.io/projects/spring-framework). 

*Note 1: If not specified module is written in [Kotlin](https://kotlinlang.org/).*

*Note 2: JavaDoc or KotlinDoc is intentionally not present.*

## Module main-application

- contains main CLI class `BankApplication`
- generated `.jar` has manifest with main class specified - you can run it with `java -jar bank.jar` command
- default host is `localhost`
- default port is `8080`
- default H2 DB directory is current dir `.`
- run it with `--help` parameter to see all parameters
- module also contains End-to-end integration test

## Module business

- contains main business logic (usually with start of DB transaction)
- `BankInitializer` to initialize bank internal accounts for Top-up and Withdrawal actions
- `AccountService` for working with accounts itself
  - `createPersonalAccount` - for creating `PERSONAL` account with specified `name` and zero `balance`
  - `createTopUpAccount` - for creating bank internal `TOP_UP` account
  - `createWithdrawalAccount` - for creating bank internal `WITHDRAWAL` account
  - `findAccount` - for finding account by it's `id`
  - `findTopUpAccount` - for finding most suitable top-up account
  - `findWithdrawalAccount` - for finding most suitable withdrawal account
- `PaymentOrderService` for creating payment requests and returning data about payment orders
  - `receivePaymentRequest` - for creating payment requests between accounts
  - `topUpRequest` - for topping up accounts
  - `withdrawalRequest` - for withdrawal requests
  - `paymentOrderState` - to get state of particular payment order
  - `listItemsForPersonalAccounts` - to list all payment order of specified personal account
  - `listItemsToProcess` - to list requests that could be processed (they are in `RECEIVED` state)
- `TransactionService` for some helper methods
  - `calculateBalance` - to calculate balance of account from credit/debit side of amount (to check if account's `balance` is correct)
  - `findAccountTransactions` - to list all transactions of specified account
- `Coordinator` is used to coordinate which payment orders and when they will be processed
  - it polls database for data to process and sends them to `Transactor`
- `Transactor` is used to process payment request
  - it uses optimistic locking to fail when concurrent access to data is detected by `VERSION` database column
  - when concurrent processing occurs only first commit wins

## Module dao

- contain DAO classes to access all three DB tables
  - `account` table for storing information about account and it's balance
  - `payment-order` for storing payment requests between accounts
  - `transaction` for storing actual money transactions
- JOOQ is used as SQL abstraction
  - also optimistic locking is used for keeping data consistency when money transfer occurred 

## Module domain

- contains domain data classes
  - `Account` - for storing information about account like it's `type`, `name`, `dateOpened`, and current `balance`
    - types are `PERSONAL`, `TOP_UP` for cash/card top-ups, `WITHDRAWAL` for eg. ATM withdrawals
  - `PaymentOrder` - for storing information about payment order request like `fromAccount` and `toAccount` transfer, `amount` of money to transfer, `state` of order, and `dateCreated` when order has been created
    - states are `RECEIVED`, `OK`, and `NO_FUNDS` when personal account does not have enough funds to finish transaction
  - `Transaction` - for actual transaction. It contains reference to `paymentOrder` and `dateTransacted` information.
    - fields `fromAccount`, `toAccount`, and `amount` are kind of duplicates to similar filed in `PaymentOrder` and are here for demo purposes

## Module rest

- contains [Jersey](https://jersey.github.io) definition of REST endpoints
- also defines `ApplicationServletContextListener` which is used as starting point for application-context
  - inspired by Spring-web module
- `RestApplication` registers endpoints into Jersey context and configures [Jackson JSON](http://fasterxml.com/) (de)serialization.
- every endpoint has to implement `Endpoint` interface with `servletContext` field which is used to lookup application context
- endpoints are: 
  - `POST /api/account` - to create account
  - `GET /api/account/{id}` - to get info about account
  - `GET /api/account/{id}/calculated-balance` - to get calculated balance for account
  - `GET /api/account/{id}/transactions` - to find all transaction for particular account
  - `POST /payment-order/transfer` - to create transfer request between accounts
  - `POST /payment-order/top-up` - to create account top-up request
  - `POST /payment-order/withdrawal` - to create account withdrawal request
  - `GET /payment-order/{id}/state` - to find state of particular payment order

## Module application-context

- inspired by Spring's IOC
- contains interface `ApplicationContext` with definition of all beans
- beans uses constructor injection
- beans are lazy created because of `.start()` and `.stop()` nature of context itself 

## Module database

- written in Java
- wraps [Hikari connection pool](https://brettwooldridge.github.io/HikariCP/) and [H2 database](http://h2database.com) using `Database` interface
  - before use `.start()` method has to be called
  - to free database resources `.stop()` has to be called
- method `.getDataSource()` is used with cooperation of `Transactional` interface from "tx module"
- this module also contains `database.sql` file with DDL scripts
  - and generated [JOOQ](http://www.jooq.org) meta-model from database 

## Module tx

- written in Java and implementation is inspired by Spring's `TransactionTemplate` class
- main interface is `Transactional` which runs actions inside current transaction
- transaction is bound to current connection from Hikari pool and saved to `ThreadLocal` holder
- when we do not have current transaction/connection bound to thread new one is obtained from pool
  - and returned after end of helper callback methods

## Module undertow-server

- written in Java and inspired by [Spring-Boot](https://spring.io/projects/spring-boot) hot it internally run selected embedded Servlet container
- wraps [Undertow HTTP server](http://undertow.io/) into `RestServer` class with `.start()` and `.stop()` methods
- using constructor you can specify:
  - `host` (`localhost` is used as default)
  - `requestedPort` (when `0` random port is selected)
  - `servletContextListener` - usually contain application context
  - `applicationClass` - Jersey application class to start Rest endpoints 
