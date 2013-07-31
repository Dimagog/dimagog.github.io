---
layout: post
title: Clojure All The Way - Knockout
categories: [clojure, clojurescript]
tags: [clojure, clojurescript, client, knockout]
tagline: Part 3 of mini-series
---

This is a third article in mini-series. The goal of mini-series is to create a Client-Server environment
where Clojure data structures are pervasive and we don't have to deal with JSON and JavaScript objects 
in Clojure/ClojureScript land (as much as possible).

This time we'll continue building on [previous post][] and will integrate ClojureScript
with [Knockout.js][ko].

[previous_post]: {{ site.url }}{% post_url 2013-07-18-clojure-all-the-way-the-client %}
[ko]: http://knockoutjs.com/

## Where we are

We start where we left off in the [previous post][]: we have a Server
that implements `echo` HTTP API and calling it returns [edn-encoded][edn] data.

And we have a Client capable of calling this API, receiving data, and [edn-decoding][edn] it.
But it displays the data (headers of our HTTP request) in a very primitive way
by simply setting text of DOM element.

## Our Goal = ClojureScript + Knockout

We'd like to nicely format received headers as HTML table.
We can of cause write ClojureScript code to build HTML for it using [Hiccup]-like library.
But where is fun in that!

It would be more entertaining to use some JavaScript data-binding library.
First, because I like [MVC][]-based approach. But more importantly because it will allow
us to explore ClojureScript integration with existing JavaScript framework.

We'll use [Knockout.js][ko] *(abbreviated [KO][] for the rest of this post)* as it happens to be my favorite
[MVVM][] framework for JavaScript. And also [KO][] claims that 
it is very smart and highly optimized in minimizing DOM modifications, so I expect it to be efficient as well.

> There is an excellent 20-min [Knockout.js demo video][ko_demo] on [KO][] web site. If you completely new to [KO][]
> I'd recommend to watch it first. It will make material below easier to understand, plus it's a
> truly [masterfully-done demo][ko_demo].

[ko_demo]: http://channel9.msdn.com/Events/MIX/MIX11/FRM08
[mvc]: http://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller
[mvvm]: http://addyosmani.com/blog/understanding-mvvm-a-guide-for-javascript-developers/
[hiccup]: https://github.com/weavejester/hiccup
[edn]: https://github.com/edn-format/edn
[clientpost]: {{ site.url }}{% post_url 2013-07-18-clojure-all-the-way-the-client %}
[serverpost]: {{ site.url }}{% post_url 2013-07-14-clojure-all-the-way-the-server %}

## Naive Knockout integration

Let's see what is the minimal effort required to integrate our code with [KO][], and then we'll
improve on it.

### Preparing input data

First we need to massage our data: headers are represented as a `map` but [KO][] requires it to be array'ish
collection if we want to use its `foreach` binding. Easy enough: `map` converted to `seq` is actually
a sequence of `vectors`, each `vector` being a key-value tuple. While we at it let's sort it by header
name as well:

{% highlight clojure %}
  (let [headers (->> "/api/echo" get-edn <! :headers (sort-by first))]
{% endhighlight %}

Calling `sort-by` is the only addition to our previous code. It converts `map` to `seq` and also
sorts it by `first` element of each tuple, i.e. by header name.

The last step is converting Clojure data to plain JavaScript data that [KO][] understands.
We do it by using `clj->js` function that will recursively convert our Clojure `vector` of `vectors` into
JavaScript `Array` of `Arrays`:

{% highlight clojure %}
(clj->js headers)
{% endhighlight %}

I know the whole premise of the series is to **avoid** such explicit conversion. Please bear with me,
we'll deal with it in following sections.

### Adding Knockout bindings to HTML

*Note: Code samples include only interesting parts, [full source code is available below](#src).*

We need to change our `default.html` as follows (only relevant parts are shown):

{% highlight html linenos %}
<head>
  <script src="http://ajax.aspnetcdn.com/ajax/knockout/knockout-2.2.1.js"></script>
  <script defer src="app.js"></script>
</head>
<body>
  <div id="content">
    <table>
      <thead><tr><th>Header</th><th>Value</th></tr></thead>
      <tbody data-bind="foreach: $root">
        <tr>
          <td data-bind="text: $data[0]"></td>
          <td data-bind="text: $data[1]"></td>
        </tr>
      </tbody>
...
{% endhighlight %}

First we add reference to [KO][] itself in *line 2*. Then we create a table *(line 7)* with static headers *(line 8)*
and [KO][]-bound body *(line 9)*. The `foreach` in *line 9* iterates over `$root` binding *(not explained yet)* expecting
it to be an array and replicates its HTML body for each element while binding `$data` to it.

In our case each element is a key-value tuple for each header represented as a 2-element array.
So `td`s in *lines 11-12* simply extract first element for the header name and second for the value.

### Applying root Knockout binding

Now to connect our ClojureScript data to [KO][] bindings in HTML we just need to call `ko/applyBindings`.
And our final code for this step looks like this:

{% highlight clojure linenos %}
(go
  (let [headers (->> "/api/echo" get-edn <! :headers (sort-by first))]
    (ko/applyBindings (clj->js headers))))
{% endhighlight %}

We obtain the data and massage it in *line 2*. Then convert Clojure data to JavaScript using `clj->js`
and pass it as a root binding to [KO][] using `ko/applyBindings` in *line 3*.

**And viola!** Our headers are shown in a nice-looking table (with little CSS help):

![table1]({{ site.url }}/assets/table1.png)

Now readers familiar with [KO][] are probably shaking their head in disgust:
no observables, and `applyBindings` should be called only once (not from some potentially reusable
`go`-routine).

And let's not forget about explicit `clj->js` call.

**Fear not! we'll take care of it right away!**

## Using computed observables

My first (but not final) improvement attempt is to use [KO][]'s [computed observable] to do automatic
`clj->js` translation. 
> [KO][] documentation says that [extenders](http://knockoutjs.com/documentation/extenders.html)
> could be a better fit, but I'm more familiar with computed observables.

[computed_observable]: http://knockoutjs.com/documentation/computedObservables.html

First we create a helper function to build our [computed observable][]:

{% highlight clojure linenos %}
(defn observable [val]
  (ko/computed
    (let [state (ko/observable (clj->js val))]
      (js-obj
        "read"  (fn [] (state))
        "write" (fn [new] (state (clj->js new)))))))
{% endhighlight %}

`ko/computed` observable *(line 2)* takes a JavaScript object created in *line 4* that specifies `read` and `write`
functions. The actual state is kept in regular `ko/observable` created in *line 3*.

[KO][] observables are functions. Calling it with no arguments returns current value and calling it
with one arguments sets its value. So `(state)` in *line 5* gets the current `state` and
`(state (clj->js new))` in *line 6* sets it (after converting `new` to JavaScript representation).

Now our main code looks like this:

{% highlight clojure linenos %}
(def view-model (observable []))
(ko/applyBindings view-model)
 
(go
  (let [headers (->> "/api/echo" get-edn <! :headers (sort-by first))]
    (view-model headers)))
{% endhighlight %}

We create our `view-model` in *line 1* using our `observable` helper, and set it to empty vector first.
Then we bind it in *line 2*, this needs to be done only once. And when we obtain our `headers` in *line 5* we set `view-model` observalble
in *line 6* (remember - observables are functions). This in turn triggers DOM update.

*Note: using global `vars` like `view-model` is not recommended in good Clojure code,
but will do for our sample code.*

**Great!** `clj->js` conversion is nowhere to be seen in our main code and we adhere to good [KO][] practices, **but ...**

### What's wrong with it?

First, getting/setting `view-model` by calling it as a function is not very *Clojury*.

Second, and more importantly, if we read `(view-model)` we'll get JavaScript-converted objects, not
the original. The original is "lost in translation". Which means that if we want to modify our
view model in ClojureScript we need to keep it separately somewhere.

And where do we usually keep changing data in Clojure? **In `refs` of cause!** At this point
a light bulb should begin hovering above your head: the `refs` in Clojure have watchers, so they can notify
whoever is interested when they change.

`refs` sound suspiciously familiar to `ko/observables`: both track changing state and both send notifications
when change happens. Can we somehow bridge the two? We certainly can!

## Using Observable refs

Let's create a helper function to create a `ko/observable` tracking Clojure `ref`:

{% highlight clojure linenos %}
(defn observable-ref [r]
  (let [state (ko/observable (clj->js @r))]
    (add-watch r state (fn [obs _ _ new] (obs (clj->js new))))
    state))
{% endhighlight %}

`observable-ref` takes a `ref` as parameter `r` (the `ref` itself, not its value!).
We create a `ko\observable` named `state` in *line 2* with initial value of `r` converted to JavaScript.
Then in *line 3* we add a watcher for the `r`.

The watcher `fn` takes 4 parameters, but we only care about 2:
`obs` is our observable `state` and `new` is the new value of the `ref` `r`. Then we simply convert `new`
value to JavaScript representation and put it into `obs`.

And finally we return `state` observable from the function so that it can be passed to [KO][].

Our main code now changes to this:

{% highlight clojure linenos %}
(def view-model (atom []))
(ko/applyBindings (observable-ref view-model))

(go
  (let [headers (->> "/api/echo" get-edn <! :headers (sort-by first))]
    (reset! view-model headers)))
{% endhighlight %}

Notice that `view-model` created in *line 1* is now a Clojure `ref` (in this case an `atom`). And we don't even save
the observable returned by our `observable-ref` helper, we just pass it directly to `ko/applyBindings`
in *line 2*.

And modifying our `view-model` *(line 6)* is as simple as modifying any other Clojure `atom`, yet DOM immediately
reflects the changes!

**Isn't it nice!** Let's have some fun with it ...

## Animating our table

Just for fun, let's pretend that our Internet connection is very slow and headers are received one by one,
slooowly. We can emulate this by `conj`ing headers to `view-model` one at a time with delay:

{% highlight clojure %}
(go
  (let [headers (->> "/api/echo" get-edn <! :headers (sort-by first))]
    (doseq [h headers]
      (<! (timeout 500))
      (swap! view-model conj h)))
{% endhighlight %}

Then we'll delay for a second and pretend that terrible computer virus eats our headers one at a time:

{% highlight clojure %}
    (<! (timeout 1000))
    (while (seq @view-model)
      (swap! view-model rest)
      (<! (timeout 500)))
{% endhighlight %}

Now delay for another second to keep the suspense, and finally restore our headers table to calm down the user:

{% highlight clojure %}
    (<! (timeout 1000))
    (reset! view-model headers)
{% endhighlight %}

**Pure Clojure data manipulation code, yet [KO][] faithfully reflects all our changes in the DOM!**

**Mission accomplished!**

Or is it? *I have tentative plans for another post in the series, but it requires a little more research.*
I'll keep you posted.

**UPDATE:** Keeping you posted, here is the [next post]({{ site.url }}{% post_url 2013-07-31-clojure-all-the-way-deep-knockout %}).

## One last thing: optimized builds

Unlike Steve Job's famous "one last things" this one is boring, **but important**. If we try to build
our ClojureScript code with `:advanced` optimization it will not work. This is because
Google Closure compiler minifies all names in produced .js file including names like `ko` and
`observable`. Certainly not what we want.

The solution is to add so-called [externs file] and feed it to Google Closure compiler, so it would
know which names it is not supposed to touch.

[externs_file]: https://developers.google.com/closure/compiler/docs/api-tutorial3#externs

Conceptually the [externs file][] is similar to C/C++ .h headers files, or .asmmeta files for C#. Ours
is called `ko.externs.js` and looks like this:

{% highlight javascript %}
var ko = {};
ko.applyBindings = function() {};
ko.observable = function() {};
ko.computed = function() {};
{% endhighlight %}

Just declarations, no code. And after adding it to `project.clj`:
{% highlight clojure %}
:externs ["cljs/ko.externs.js"]
{% endhighlight %}

... we can build our optimized code, and it will work correctly:

    lein cljsbuild once opt

If you are wondering how big is produced optimized `app.js` file (I sure was), it is `118'871 bytes`.

## <a name="src"> </a> Source code
Full source code [can be found on GitHub][github].

If you want to build and run it locally, execute:

    git clone https://github.com/Dimagog/dimagog.github.io.git -b ClojureAllTheWay3 --single-branch ClojureAllTheWay3
    cd ClojureAllTheWay3
    lein ring server

[github]: https://github.com/Dimagog/dimagog.github.io/tree/ClojureAllTheWay3
