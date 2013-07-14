---
layout: post
title: Clojure All The Way - The Server
category: clojure
tags: [clojure, ring, compojure, server]
tagline: Part 1 of mini-series
---

This is a first article in mini-series. The goal of mini-series is to create a Client-Server environment
where Clojure data structures are pervasive and we don't have to downgrade them to JSON (as much as possible).

In this post we'll develop a Web server in Clojure (using [Ring][] and [Compojure][]) that exposes
simple API to serve Clojure data structures in their native representation
([EDN][]-encoded if you want to be fancy).

[edn]: https://github.com/edn-format/edn
[ring]: https://github.com/ring-clojure/ring
[compojure]: https://github.com/weavejester/compojure

## The Basics (are not included)
The basics of creating [Ring][]+[Compojure][] Web server are covered very well on Interwebs.
In this post I'll only focus on EDN-returning API creation.

{% include JB/setup %}

## Starting point

Let's start with vanilla web server:

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

For my [previous post][prev] I've created the simplest Ring-server I can think of to demonstrate
making HTTP requests from the client. We'll start from there (including client for testing),
and will add [Compojure][] and then an  `echo` API that simply echoes back the client's HTTP request.
*[Full source code is available below](#src)*

[prev]: {{ site.url }}{% post_url 2013-07-12-making-http-requests-from-clojurescript-with-core.async %}

## Serving the site itself (static files)

## <a name="src"> </a> Source code TODO
Full source code [can be found on GitHub][github].

If you want to build and run it locally, execute:

    git clone https://github.com/Dimagog/AsyncGET.git
    cd AsyncGET
    lein ring server

[github]: https://github.com/Dimagog/AsyncGET
