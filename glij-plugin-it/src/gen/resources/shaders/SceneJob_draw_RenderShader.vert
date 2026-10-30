#version 460 core

layout(location = 0) in vec3 position;
out vec3 shade;
uniform sampler2D image;
uniform float brightness;

void main() { 
                gl_Position = vec4(position, 1.0);
                shade = vec3(brightness) + texture(image, vec2(0.5)).rgb;
            }
