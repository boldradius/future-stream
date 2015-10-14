# FutureStream

A `FutureStream[A, E]` represents an immutable sequence of eventually available data of type `A`, potentially
terminated by a special value of type `E`. Such a stream is partially lazy: observations may trigger
the evaluation of one element or more. Inspecting the stream generally returns values wrapped in a Future,
since they may not yet be available. Conversely, creating a FutureStream requires an implicit ExecutionContext where
future work will be performed. 
 
## Immutability and memory reclamation

In contrast to more transient forms of streams, such as Akka Streams, FutureStreams are immutable values that
provide direct and permanent access to data. The values it contains never change or disappear until all references to
the stream are discarded. 

The structure of a FutureStream is recursive, just like a list. Whenever an element is accessible, so is
the tail of the stream (the tail contains the all the elements except the first one). Keeping a reference to the tail does
not keep a reference to the whole stream, so memory needed to store the initial elements of a stream can be
reclaimed by only keeping references to the stream past those elements (therefore referencing only streams that exclude
those elements).

## Foundation

"[...] exactly the same techniques used to model infinite lists are
suitable for modelling interactive programs [...]" -- Richard Bird & Phillip Wadler in Introduction to Functional Programming (1988)

This stream representation is essentially a Scala implementation of the treatment of interactivity proposed in
the work quoted above. The lists in the book are lazy. Here we also add Futures to avoid the expense of blocking
on the JVM. We also generalize by a adding a distinct terminating element to better handle failure and finite streams.
But fundamentally this is the same idea, where interactive input and output are not viewed as side effects
but as pure stream values.
