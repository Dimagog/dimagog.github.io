---
layout: post
title: Clojure All The Way - The Server
category: clojure
tags: [clojure, ring, compojure, server]
tagline: Part 1 of mini-series
---

This is a first article in mini-series. The goal of mini-series is to create a Client-Server environment
where Clojure data structures are pervasive and we don't have to deal with JSON and JavaScript objects 
in Clojure/ClojureScript land (as much as possible).

In this post we'll develop a Web server in Clojure (using [Ring][] and [Compojure][]) that exposes
simple API to serve Clojure data structures in their natural text representation
(fancy term for this is [edn-encoded][edn]).

[edn]: https://github.com/edn-format/edn
[ring]: https://github.com/ring-clojure/ring
[compojure]: https://github.com/weavejester/compojure

## The Basics are not covered
The basics of creating [Ring][]+[Compojure][] Web server are covered very well on Interwebs.
In this post I'll only focus on creating API returning [edn-encoded][edn] Clojure data.

## Serving the site itself (static files)

*Note: Code samples include only interesting parts, [full source code is available below](#src).*

Let's start with vanilla web server that simply serves static files:

{% highlight clojure linenos %}
(defroutes app
  (GET "/" [] (file-response "default.html" {:root "html"}))
  (files "" {:root "html"})
  (not-found "<h1>404 Page not found</h1>")
)
{% endhighlight %}

* line 2 returns (not redirects to) `default.html` when server root is accessed
* line 3 serves static files from "html" directory
* line 4 is only hit if static file is not found and returns 404

## Adding Echo API

Now let's add an `echo` API that simply echoes back the client's HTTP request.

*To be precise:* upon receiving HTTP `GET /api/echo` request the server will respond with
`200 OK` and body containing [edn-encoded][edn] Clojure representation of request (a map of maps, strings and numbers).

Of cause we are not going to do heavy-lifting ourselves - that's the whole point of [Ring][].
To paraphrase Apple's commercials "there is a *middleware* for that".
And it's called `ring.middleware.format-response`. Let's use it:

{% highlight clojure linenos %}
(defroutes handler
  (GET "/" ...)
  (GET "/api/echo" request
       {:status 200
        :body request})
  ...)

(def app (-> handler
             wrap-clojure-response))
{% endhighlight %}

The `app` was renamed to `handler` *(line 1)* and new `app`
wraps this `handler` into `wrap-clojure-response` *(lines 8-9)*.

The new [Compojure] route *(lines 3-5)* simply takes a `request` (a Clojure map) and builds a
200-response from it setting `:body` to `request` (still a Clojure map).

**And the `wrap-clojure-response` *middleware* is the one converting Clojure map `:body` returned
by `handler` to [edn-encoded][edn] textual representation.**

There is one problem with this code: `:body` of `request` that we are sending back is not
a Clojure data structure. It's a Java object of `HttpInput org.eclipse.jetty.server.HttpInput` type.
That's not good: it cannot be [edn-encoded][edn] and Client won't be able to decode it anyway.
A small tweak removes it from the response we are sending *(call to `dissoc` in line 3)*:

{% highlight clojure linenos %}
  (GET "/api/echo" request
       {:status 200
        :body (dissoc request :body)})
{% endhighlight %}

## Testing
To test our Server we can use a slightly modified client from my [earlier post][earlier].
And sure enough it shows client's request returned back to us.

But ClojureScript Client still treats response as text (not structured Clojure data).
We'll deal with it in the next post.

[earlier]: {{ site.url }}{% post_url 2013-07-12-making-http-requests-from-clojurescript-with-core.async %}

## <a name="src"> </a> Source code
Full source code [can be found on GitHub][github].

If you want to build and run it locally, execute:

    git clone https://github.com/Dimagog/dimagog.github.io.git -b ClojureAllTheWay1 --single-branch ClojureAllTheWay1
    cd ClojureAllTheWay1
    lein ring server

[github]: https://github.com/Dimagog/dimagog.github.io/tree/ClojureAllTheWay1
