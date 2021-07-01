#!/bin/sh

for f in $(seq 1 1000)
do
  cat <<EOF
    <li>
      <a href="text/p$f.xhtml">Page $f. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin non blandit justo. Sed ac varius dui. In ac ipsum at ligula suscipit condimentum vitae a nisl. Maecenas quis magna dolor. Pellentesque iaculis purus rutrum dolor mattis, at pellentesque enim tristique. Vestibulum ut tellus non lacus tempus pretium lobortis nec leo. Praesent sollicitudin finibus nulla quis maximus. Proin condimentum, sem sed dictum commodo, quam sapien fermentum augue, finibus consequat mi sapien ac justo. Ut tellus augue, malesuada quis pellentesque a, vulputate sit amet nisi. Etiam pretium commodo magna, quis accumsan ligula ultrices sed. Mauris ut turpis pretium, dignissim nibh et, interdum felis. Mauris facilisis neque quis mauris lobortis dignissim.</a>
    </li>
EOF
done
