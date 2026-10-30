#version 460 core

in vec3 shade;
layout(location = 0) out vec4 color;
uniform sampler2D image;
uniform float brightness;

void main() {
                color = vec4(shade, 1.0);
            }
