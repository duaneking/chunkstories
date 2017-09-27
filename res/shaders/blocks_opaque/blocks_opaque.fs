#version 330
//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

//Passed variables
in vec2 worldLight; //Computed in vertex shader
in float fresnelTerm;
in float rainWetness;
in vec3 normalPassed;
in vec4 vertexPassed;
in vec2 texCoordPassed; // Coordinate
in vec3 eyeDirection; // eyeDirection-position

//Blocks textures
uniform sampler2D diffuseTexture; // Blocks diffuse texture atlas
uniform sampler2D normalTexture; // Blocks normal texture atlas
uniform sampler2D materialTexture; // Blocks material texture atlas
uniform sampler2D vegetationColorTexture; // Blocks material texture atlas

//Block and sun Lightning
uniform float sunIntensity; // Adjusts the lightmap coordinates
uniform vec3 sunPos; // Sun position

//Common camera matrices & uniforms
uniform mat4 projectionMatrix;
uniform mat4 projectionMatrixInv;

uniform mat4 modelViewMatrix;
uniform mat4 modelViewMatrixInv;

uniform mat3 normalMatrix;
uniform mat3 normalMatrixInv;

uniform float mapSize;

//Gamma constants
<include ../lib/gamma.glsl>
<include ../lib/transformations.glsl>

<include ../lib/normalmapping.glsl>

out vec4 outDiffuseColor;
out vec4 outNormalColor;
out vec4 outMaterialColor;

void main(){
	//Check some normal component was made available
	vec3 normal = normalPassed;
	float normalGiven = length(normalPassed); //Expected to be ~1
	
	//Grabs normal from texture and corrects the format (stored as unsigned int texture)
	vec3 normalMapped = texture(normalTexture, texCoordPassed).xyz;
    normalMapped = normalMapped * 2.0 - 1.0;
	
	//Apply it
	normal = perturb_normal(normal, eyeDirection, texCoordPassed, normalMapped);
	normal = normalize(normalMatrix * normal);
	
	//If no normal given, face camera
	normal = mix(vec3(0,0,1), normal, normalGiven);
	
	//Basic texture color
	vec3 surfaceDiffuseColor = texture(diffuseTexture, texCoordPassed).rgb;
	
	//Texture transparency
	float alpha = texture(diffuseTexture, texCoordPassed).a;
	
	//Discard pixels too faint
	if(alpha <= 0.1)
		discard;
	//Color pixels with some alpha component with the vegetation color
	else if(alpha < 1)
		surfaceDiffuseColor *= texture(vegetationColorTexture, vertexPassed.xz / vec2(mapSize)).rgb;
		
	vec4 material = texture(materialTexture, texCoordPassed);

	float specularity = (material.g + rainWetness) * fresnelTerm;
	//We use a fancier equation if enabled
	<ifdef perPixelFresnel>
		float dynamicFresnelTerm = 0.0 + 1.0 * clamp(0.7 + dot(normalMatrix * normalize(eyeDirection), vec3(normal)), 0.0, 1.0);
		specularity = (material.g + rainWetness) * dynamicFresnelTerm;
	<endif perPixelFresnel>
	
	//Diffuse G-Buffer
	outDiffuseColor = vec4(surfaceDiffuseColor, 1.0);
	outNormalColor = vec4(encodeNormal(normal).xy, specularity, 1.0);
	outMaterialColor = vec4(worldLight, material.r, material.a);
}