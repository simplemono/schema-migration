# schema-migration

A library for Datomic schema migrations.

Ideally, you avoid migrations entirely in your Datomic database and [only grow
your
schema](https://blog.datomic.com/2017/01/the-ten-rules-of-schema-growth.html),
to avoid breakage like removing schema or changing the meaning of existing
schema.

In practice, we made a few mistakes in our schema that can only be 'corrected'
by breakage. While only having schema growth is a noble goal, it demands that
you get your schema 'correct' on the first try, at least to a degree that avoids
breakage down the road.

This library makes different trade-offs and migrations can commit breakage. The
limitations are:

- Only one application should read your database so that you don't break
  another application that expects the old schema.

- The schema and the code should be tightly coupled, meaning your code should
  always run on the schema version that was designed for this code version.

- This library's approach will only work if your Datomic database and migrations
  stay small.

The last limitation got us into trouble with our huge Datomic database, which is
used for all customers of our SaaS business. While a Datomic transactor prepares
transactions in parallel, it commits them one after another (strict
serializability). A huge transaction for a migration may take several seconds or
even minutes to complete. During this time, no other customer can do any writes
on the system, and their work is blocked until your huge transaction is
committed. Therefore, we manually did most of our migrations and split them into
several smaller transactions to reduce the impact on our customers. We can only
use this library in our new architecture where each customer (a team of users)
has its own small Datomic database. While the migrations are performed, this
customer cannot do any writes or even see a progress bar. However, all other
customers can continue to commit transactions to their Datomic database.
