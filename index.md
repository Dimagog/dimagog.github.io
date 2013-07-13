---
layout: page
title: Dimagog Blog
tagline: Latest Posts
---
{% include JB/setup %}

{% assign sumwords = 50 %}
{% assign maxposts = 10 %}

<table class="posts">
  {% for post in site.posts limit: maxposts %}
    <tr>
      <td>
        <h5><i>{{ post.date | date_to_string }}</i> &raquo;</h5>
      </td>
      <td width="85%">
        <h4><a href="{{ BASE_PATH }}{{ post.url }}">{{ post.title }}</a></h4>
        {% assign postwords = post.content | strip_html %}
        {% assign totalwords = postwords | number_of_words %}
        {% unless post.noexcerpt %}
          {% assign excerptwords = post.excerpt | strip_html %}
          {% assign sumwords = excerptwords | number_of_words %}
          <span>Excerpt: {{ excerptwords }}</span>
        {% else %}
          <span>Summary: {{ postwords | truncatewords: sumwords }}</span>
        {% endunless %}
        {% if totalwords > sumwords %}
          <a href="{{ BASE_PATH }}{{ post.url }}"><i>continue reading ({{totalwords}} words)</i></a>
        {% endif %}
      </td>
    </tr>
  {% endfor %}
</table>
{% assign totalposts = site.posts | size %}
{% if totalposts > maxposts %}
<h4><a href="{{ site.url }}{{site.JB.archive_path}}">Read more in Archive</a></h4>
{% endif %}
