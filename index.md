---
layout: page
title: Dimagog Blog
tagline: Latest Posts
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
