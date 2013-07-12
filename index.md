---
layout: page
title: Latest Posts
tagline: Welcome to my Blog
---
{% include JB/setup %}

<table class="posts">
  {% for post in site.posts %}
    <tr>
      <td>
        <h5><i>{{ post.date | date_to_string }}</i> &raquo;</h5>
      </td>
      <td width="85%">
        <h4><a href="{{ BASE_PATH }}{{ post.url }}">{{ post.title }}</a></h4>
        <span>{{ post.excerpt }}</span>
      </td>
    </tr>
  {% endfor %}
</table>
