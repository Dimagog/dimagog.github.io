---
layout: post
title: Clojure All The Way - The Client
categories: [clojure, clojurescript]
tags: [clojure, clojurescript, client, core.async, async]
tagline: Part 2 of mini-series
---

This is a second article in mini-series. The goal of mini-series is to create a Client-Server environment
where Clojure data structures are pervasive and we don't have to deal with JSON and JavaScript objects 
in Clojure/ClojureScript land (as much as possible).

The [previous post][serverpost] dealt with the Server part, now let's look at the Client side.

## Starting Point

We start where we left off in the [previous post][serverpost]: we have a Server
that implements `echo` HTTP API and calling it returns [edn-encoded][edn] data.

And we have a Client capable of calling this API and receiving data, but it still
treats it as text, and not as structured Clojure data. The client was actually explained
not in the last post, but in [post before that][clientpost].

[edn]: https://github.com/edn-format/edn
[clientpost]: {{ site.url }}{% post_url 2013-07-12-making-http-requests-from-clojurescript-with-core.async %}
[serverpost]: {{ site.url }}{% post_url 2013-07-14-clojure-all-the-way-the-server %}

## First, small improvement to GET function

*Note: Code samples include only interesting parts, [full source code is available below](#src).*

The `GET` function in previous [client post][clientpost] was implemented using `(>! ch ...)` 
inside `go`-routine *(line 6)*:

{% highlight clojure linenos %}
(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (let [res (-> event .-target .getResponseText)]
                  (go (>! ch res)
                      (close! ch)))))
    ch))
{% endhighlight %}


In comments to [that post][clientpost] Alexander Solovyov suggested using `put!` instead.
It's a good idea (thanks Alexander!) since we don't care when channel write completes.
Here is the change:

{% highlight clojure linenos %}
(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                  (put! ch (-> event .-target .getResponseText))
                  (close! ch)))
    ch))
{% endhighlight %}

the inner `let` and `go` blocks are gone and we use `put!` in *line 5* instead.

Now back to our main topic ...

## Getting Clojure data from Server response

Let's add a new function `get-edn` that uses `GET` function above to get data from the Server, and then
converts it to Clojure data structures, i.e. [edn-decodes][edn] it. It is actually very simple, all we need
to do is call `read-string` function from `cljs.reader` namespace.

And in addition to being useful for our purposes it will also show how async functions compose.

{% highlight clojure linenos %}
(defn get-edn [url]
  (go 
    (-> (GET url)
        <!
        read-string)))
{% endhighlight %}

Let's look at it from inside out. In *line 3* we call our `GET` that returns a `channel` that will eventually
contain result (text). In *line 4* we read the value from this `channel` potentially "parking" this activity, but not blocking.
This is why we need this code wrapped in `go`-routine. And then in *line 5* we pass returned text to `read-string` that 
[edn-decodes][edn] it and returns Clojure data structure (let's call this value `result`).

But what happens next? How is `result` propagated to the caller of `get-edn`? To answer that we need to understand
what `get-edn` returns. Following normal Clojure rules `get-edn` returns the value returned by `go` form.
This is a `channel`, now who writes to this `channel`? The `go` form does. Here is how `go` works:
* first it creates a `channel` and promptly returns it to the caller
* *eventually* it evaluates all statement inside, potentially "parking" this activity when `<!` and `>!` forms are encountered
* it writes the value of the last statement to the `channel`, `close!`es the `channel` and completes.

In our case the last and only statement inside `go` is our "conveyor" producing `result` defined two paragraphs ago,
i.e. decoded response. The `go` form writes `result` to the `channel` it has created and this is how it gets to `get-edn` caller.

**And this is how async functions compose!** Notice that we didn't even have to create a `channel` explicitly in `get-edn`!

The reason we had to create `channel` in `GET`
function is because we were converting callback-based API to asynchronous one. The caller of our `fn` in there was not async-aware,
and actually ignored the return value. So we had to propagate it to `GET` caller thru manually-created `channel` `ch`.

## Calling get-edn function

To prove to ourselves that `get-edn` returns Clojure map let's call some function on it before displaying it,
for example let's extract only `:headers`:

{% highlight clojure linenos %}
(go
  (dom/set-text! (sel1 :#log)
                 (-> (get-edn "/api/echo")
                     <!
                     :headers))
{% endhighlight %}

The whole block is wrapped in `go` routine because we use `<!` (read channel) form.
We call `get-edn` in *line 3* that returns a channel, we read from it in *line 4*, potentially "parking" (but again, not blocking) this activity.
Then we call `:headers` in *line 5* to get only headers portion of respose proving that result is actually a Clojure map.

And if you run the sample code, you'll see the headers displayed.

Now this is nice, but the way they are displayed is kind of lame. We'll do something about it in the next post, while staying on topic of
using Clojure data structures as much as possible.

## <a name="src"> </a> Source code
Full source code [can be found on GitHub][github].

If you want to build and run it locally, execute:

    git clone https://github.com/Dimagog/dimagog.github.io.git -b ClojureAllTheWay2 --single-branch ClojureAllTheWay2
    cd ClojureAllTheWay2
    lein ring server

[github]: https://github.com/Dimagog/dimagog.github.io/tree/ClojureAllTheWay2
