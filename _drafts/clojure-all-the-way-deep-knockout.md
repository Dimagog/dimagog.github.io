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

{% highlight clojure linenos %}
{% endhighlight %}

## <a name="src"> </a> Source code
Full source code [can be found on GitHub][github].

If you want to build and run it locally, execute:

    git clone https://github.com/Dimagog/dimagog.github.io.git -b ClojureAllTheWay3 --single-branch ClojureAllTheWay3
    cd ClojureAllTheWay3
    lein ring server

[github]: https://github.com/Dimagog/dimagog.github.io/tree/ClojureAllTheWay3