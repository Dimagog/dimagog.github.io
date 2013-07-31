---
layout: post
title: Clojure All The Way - Deep Knockout
categories: [clojure, clojurescript]
tags: [clojure, clojurescript, javascript, knockout, proxy]
tagline: Part 4 of mini-series
---

This is a fourth and final article in mini-series. The goal of mini-series is to create a Client-Server environment
where Clojure data structures are pervasive and we don't have to deal with JSON and JavaScript objects 
in Clojure/ClojureScript land (as much as possible).

Building up on the [previous post][] we will achieve much deeper integration of Clojure data structures with
[Knockout.js][ko]. But it comes with a price ...

[previous_post]: {{ site.url }}{% post_url 2013-07-22-clojure-all-the-way-knockout %}
[ko]: http://knockoutjs.com/

## Our Goal

By the end of the [previous post][] we've implemented `observable-ref` that completely hides
Clojure-to-JavaScript conversion call to `clj->js`, but *we know* it's still there.

Maybe it's silly to worry about it, but I still do. So what if we set our mind to get rid of it completely,
and have "turtles all the way down"? That's my goal for this post and depends on point of view if
it is achieved here or not :-).

## Know the Enemy

Let's have a detailed look at what our Clojure data structures are and what our "enemy" (for this post)
`clj->js` does for us.

The data we are rendering (headers of HTTP request) is a Clojure `vector` of 2-element `vectors` of `strings`.
In type-safe language like C# or Java it would be `Vector<Vector<string>>`.

The `clj->js` function recursively converts our Clojure `vector` of `vectors` into
JavaScript `Array` of `Arrays`. I.e. not only outer `vector` is converted, but each element of it
(inner `vector` of `strings`)
is also converted to `Array`. The recursion stops there because `string` representation is the same
across ClojureScript and JavaScript and no conversion is needed.

## The Easy part - Inner vectors

Later in this post it will become clear why this is the easy part, but let's start with inner `vectors`.

*Note: Code samples include only interesting parts, [full source code is available below](#src).*

### Shallow outer vector conversion
We want to stop converting inner `vectors` into `Arrays`. I.e. we don't need a deep recursive conversion of outer
`vector` that `clj->js` provides but instead want to convert it to `Array` and leave its elements intact.
The result of this step should become `Array` of `vectors` of `strings`.

This is easy, we can use `into-array` function in place of `clj->js` in `observable-ref` function:

{% highlight clojure linenos %}
(defn observable-ref [r]
  (let [state (ko/observable (into-array @r))]
    (add-watch r state (fn [obs _ _ new] (obs (into-array new))))
    state))
{% endhighlight %}

The only changes compared to [previous post][] are `into-array` calls in *lines 2 and 3* where
`clj->js` calls used to be.

Of cause if we try to render HTML now it won't work because our [KO][] bindings look like this:

{% highlight html %}
<td data-bind="text: $data[0]"></td>
<td data-bind="text: $data[1]"></td>
{% endhighlight %}

and `$data[0]` access syntax does not work for `PersistentVectors`.

### Accessing PersistentVector elements from JavaScript code

If we examine `PersistentVector` from pure JavaScript perspective we'll see the `nth` method that
we want to call to get `vector`'s element. It looks like this:

{% highlight javascript %}
cljs$core$IIndexed$_nth$arity$2: function (coll, n)
{% endhighlight %}

We certainly *can* call it from JavaScript, and it works:

{% highlight javascript %}
$data.cljs$core$IIndexed$_nth$arity$2($data, 0)
{% endhighlight %}

but it's not very user-friendly to say the least.

Fortunately JavaScript is a very flexible and dynamic language. We can easily extend `PersistentVector` with
helper `get` function that we need:

{% highlight clojure linenos %}
(aset (aget [] "__proto__")
      "get"
      (fn [index]
        (this-as this
                 (nth this index))))
{% endhighlight %}

We use empty vector `[]` in *line 1* to get to `PersistentVector`'s *prototype*. And add another method
`get` that takes an `index` and returns element at that index for `this`.
`this-as` in *line 4* gives us access to `this` from ClojureScript.

now we can change our [KO][] bindings in HTML file to use `get` method:

{% highlight html %}
<td data-bind="text: $data.get(0)"></td>
<td data-bind="text: $data.get(1)"></td>
{% endhighlight %}

Not too bad, and rather straight-forward. At this point our Client works again and we can see the table
of HTTP headers rendered correctly.

## The Hard part - Outer vector

The only piece of data conversion logic we have left is `into-array` calls that convert our outer `vector`
into JavaScript `Array`. If we get rid of it - we've reached our goal.

### Why it's hard

But this is where it becomes tricky. The problem is that we use [KO][]'s `foreach` binding to iterate
over our collection of headers:

{% highlight clojure linenos %}
<tbody data-bind="foreach: $root">
{% endhighlight %}

And `foreach` expects it to be JavaScript `Array`. **Period.** No kidding.

I've tried to teach [KO][] to iterate over custom collection, and [I've asked KO group][ko_thread].
It's not possible for now.

[ko_thread]: https://groups.google.com/forum/#!topic/knockoutjs/P2XXc9q6k04

### Should we give up?

At this point we still have achieved a tangible improvement: the *elements* of our collection are
not converted anymore, so even when we create a copy of outer vector it is a shallow copy:
only the "skeleton" is copied, but elements are reused. And they potentially have a bigger memory
footprint.

However there is a saying:
> If the mountain will not come to Muhammad, then Muhammad will go to the mountain.

In our case: if `foreach` would not take anything but `Array` then `vector` should become an `Array`.
Or at least pretend to be one.

Now how do we pretend? The access pattern from [KO][] is to read `length` property first and then
access fields `[0], [1] ... [length-1]`. We can easily add all these fields to our `vector` but
it does not buy us anything: we would copy all `vector` elements into fields `0`, `1`, etc.
It's no better than `into-array` call.

If only there was a way to intercept field access calls in JavaScript ...

### WARNING: Danger Zone

To the best of my knowledge there is no portable way to intercept field access in JavaScript.
So we'll have to use **JavaScript "Experimental Features"** that are already implemented by some
modern browsers (Firefox and Chrome support what we need), but are not standard yet and may not even be enabled by default.

**This is why the rest of this post is not practical \[for now\] but hopefully still entertaining.**

If you are willing to proceed and use Chrome you must navigate to <a href="chrome://flags">chrome://flags</a>
(enter it manually in address bar, navigating from this page would not work), find "Enable Experimental JavaScript"
feature and enable it.

### ECMAScript 6 Proxy API

ECMAScript 6 draft specifies [Direct Proxies][] - a mechanism to create a proxies that:
> ... enable ES programmers to represent virtualized objects (proxies). In particular,
> to enable writing generic abstractions that can intercept property access on ES objects.

[direct proxies]: http://wiki.ecmascript.org/doku.php?id=harmony:direct_proxies

Exactly what we need!

### Wrapping outer vector in a Proxy

A new helper function `vector-as-array` makes `vector` sort-of look like `Array` in a narrow sense
required by [KO][]. This is by no means a full proxy to emulate `Arrays`, just a bare minimum
required for our purposes.

[KO][] only needs 2 things from `Array`: `length` field and `0`, `1`, etc. fields. Well, we can give it
what it wants:

{% highlight clojure linenos %}
(defn vector-as-array
  "Creates JS Proxy around Clojure vector to make it look like JS array,
  without copying data"
  [v]
  (.create js/Proxy
           (js-obj
             "get" 
               (fn [_ prop]
                 (case prop
                   "length" (count v)
                   (get v prop)))
             "getPropertyDescriptor"
               (fn [obj prop]
                 (.getOwnPropertyDescriptor js/Object js/Array prop)))))
{% endhighlight %}

The `get` function *(lines 7-11)* checks if property name is `length` and returns `count` of `vector` `v` if it is
*(line 10)*.
Otherwise *(line 11)* it simply delegates to `vector`'s `get` function to fetch individual element.

The `getPropertyDescriptor` *(lines 12-14)* is required for when [KO][] does "reflection" on our "array"
(like checking if "length" property exists) and simply delegates calls to `js/Array`.

### Finish Line

The last change is to modify `observable-ref` once again, this time replacing `into-array` calls
with newly-created `vector-as-array` function:

{% highlight clojure %}
(defn observable-ref [r]
  (let [state (ko/observable (vector-as-array @r))]
    (add-watch r state (fn [obs _ _ new] (obs (vector-as-array new))))
    state))
{% endhighlight %}

And it's hard to believe, but **IT WORKS!**

## Conclusion

Here is a quick summary of our accomplishments:

* We've marshaled Clojure data structure created on the Server all the way to the Client using [edn encoding][edn]
which is a natural textual representation for Clojure data.
* We've received this data on the client using core.async to make our code look sequential
* Then we've called regular Clojure functions on received data to extract and sort the headers
* Finally we've created [KO][] bindings directly to Clojure data without EVER converting it to JavaScript.

Granted, the final step required sacrifices (using experimental JavaScript features), and this is why
I've mentioned above that it depends on reader's point of view if final goal was achieved or not.

**I've certainly achieved my goal of learning new interesting technologies and I had lots of fun along the way!**

*And if anyone ever reads this, I hope you did too :-).*

[edn]: https://github.com/edn-format/edn

## <a name="src"> </a> Source code
Full source code [can be found on GitHub][github].

If you want to build and run it locally, execute:

    git clone https://github.com/Dimagog/dimagog.github.io.git -b ClojureAllTheWay4 --single-branch ClojureAllTheWay4
    cd ClojureAllTheWay4
    lein ring server

[github]: https://github.com/Dimagog/dimagog.github.io/tree/ClojureAllTheWay4
