package controller

// swaggerUIPage is the Swagger UI entry point. The assets it references are
// served from the embedded swagger-ui distribution on the same route group, so
// the documentation works without internet access.
const swaggerUIPage = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Product Service API</title>
	<link rel="stylesheet" href="./swagger-ui.css">
	<link rel="icon" type="image/png" href="./favicon-32x32.png" sizes="32x32">
</head>
<body>
	<div id="swagger-ui"></div>
	<script src="./swagger-ui-bundle.js"></script>
	<script src="./swagger-ui-standalone-preset.js"></script>
	<script>
		window.onload = function () {
			window.ui = SwaggerUIBundle({
				url: "./swagger.json",
				dom_id: "#swagger-ui",
				deepLinking: true,
				presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
				plugins: [SwaggerUIBundle.plugins.DownloadUrl],
				layout: "StandaloneLayout"
			});
		};
	</script>
</body>
</html>`
