---
layout: post
title: Making http requests from ClojureScript with core.async
categories: [clojure, clojurescript]
tags: [clojure, clojurescript, async, core.async]
---
I've always been a big fan of `async/await` feature in C# (long before it was publicly available).
So, naturally, I was very excited to read the [announcement] about Clojure [core.async][] library. 
Even more so after I've learned that it works for ClojureScript as well.

Here is an example of using [core.async][] to convert callback-based `goog.net.XhrIo` `send` API to
sequential-looking one.

[core.async]: https://github.com/clojure/core.async
[announcement]: http://clojure.com/blog/2013/06/28/clojure-core-async-channels.html

## Leningen config (boring stuff)

{% highlight clojure %}
(defproject ...
  :plugins [[lein-cljsbuild "0.3.2"]]
  :hooks [leiningen.cljsbuild]
  :repositories {
    "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
  }
  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [org.clojure/clojurescript "0.0-1820"] 
    [org.clojure/core.async "0.1.0-SNAPSHOT"]
  ...
{% endhighlight %}

We need Sonatype because it is hosting SNAPSHOTs, and there is no release version of [core.async][] available yet.
Also I'm not using the latest version of ClojureScript because Light Table does not like it.

## Defining GET function (cool stuff)
First the `ns` header:
{% highlight clojure %}
(ns app
  (:require
    [goog.net.XhrIo :as xhr]
    [cljs.core.async :as async :refer [chan close!]])
  (:require-macros
    [cljs.core.async.macros :refer [go alt!]]))
{% endhighlight %}

It's not idiomatic to have all uppercase function names in Clojure, but since this function mimics
http GET method we'll make an exception for it.

The idea is to return a channel from GET function that will have a result of `send` call
when it completes. The callback simply extracts the result and writes it to the channel:
{% highlight clojure %}
(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (let [res (-> event .-target .getResponseText)]
                  (go (>! ch res)
                  (close! ch)))))
    ch))
{% endhighlight %}

You may think that callback could be simplified as:
{% highlight clojure %}
; WARNING: Broken code
              (fn [event]
                (go (>! ch (-> event .-target .getResponseText))
                (close! ch)))))
{% endhighlight %}
**but that does not work**. This version tries to extract result from `event` inside `go`
routine which will execute *eventually*. By the time it runs `event` does not have the result anymore.

Also it's a good idea to `close!` the channel `ch` after we write the result `res`, so if anyone tries
to read from `ch` again by mistake will get `nil` returned immediately.

## Using GET function (even cooler stuff)
Let's define a helper `log` function first:
{% highlight clojure %}
(defn log [s]
  (.log js/console (str s))))
{% endhighlight %}

And now we can call our `GET` function and print the result:

{% highlight clojure %}
(go
  (log (<! (GET "http://dimagog.github.io/sitemap.txt"))))
{% endhighlight %}
