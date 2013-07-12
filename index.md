---
layout: page
title: Recent Posts
tagline: Welcome to my Blog
---
{% include JB/setup %}

<ul class="posts">
  {% for post in site.posts %}
    <li>
      <h4><i>{{ post.date | date_to_string }}</i> &raquo; <a href="{{ BASE_PATH }}{{ post.url }}">{{ post.title }}</a></h4>
      <span>{{ post.excerpt }}</span>
    </li>
  {% endfor %}
</ul>
