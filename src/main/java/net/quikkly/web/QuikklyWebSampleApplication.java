package net.quikkly.web;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.quikkly.core.Pipeline;
import net.quikkly.core.QuikklyCore;
import net.quikkly.core.ScanResult;
import net.quikkly.core.Skin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.boot.json.JsonParser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.ResponseEntity.ok;

@SpringBootApplication
@EnableCaching
@Slf4j
public class QuikklyWebSampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuikklyWebSampleApplication.class, args);
	}

	@Bean
	String bluePrint() throws IOException {
		return StreamUtils.copyToString(this.getClass().getResourceAsStream("/blueprint_default.json"), Charset.forName("UTF-8"));
	}

	@Bean
	Pipeline pipeline(String bluePrint) throws RuntimeException {
		return QuikklyCore.buildPipeline(bluePrint);
	}
}

@Data
@Accessors(chain = false)
class KeyValue {
	private String key;
	private String value;

	public KeyValue(String key, String value) {
		this.key = key;
		this.value = value;
	}
}

@Controller
@Slf4j
class QuikklyController {

	private final Pipeline pipeline;
	private final QuikklyService service;

	public QuikklyController(Pipeline pipeline, QuikklyService service) {
		this.pipeline = pipeline;
		this.service = service;
	}

	@GetMapping(value = "/code/{id}", produces = {"image/svg+xml"})
	public ResponseEntity<String> getScanCode(@PathVariable("id") Long id,
											  @RequestParam(value = "template", required = false, defaultValue = "template0001style1") String template) {

		return ok(pipeline.generateSvg(template, BigInteger.valueOf(id), new Skin()));
	}

	@GetMapping(value = "/templates", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<KeyValue>> getTemplates() {
		return ok(service.getTemplates());
	}

	@PostMapping("/scan")
	public ResponseEntity<String> scanFile(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) throws IOException {

		System.out.println(file.getContentType());
		BufferedImage image = ImageIO.read(file.getInputStream());
		byte[] buffer = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		try {
			ScanResult result = pipeline.scanFrame(buffer, 2, image.getWidth(), image.getHeight(), image.getWidth() * image.getColorModel().getNumComponents());
			return ok(String.valueOf(result.tags[0].dataLong));
		}
		catch(Exception e) {
			log.error(e.getMessage());
		}
		return ok("N/A");
	}


}


@Service
class QuikklyService {

	private final String bluePrint;

	public QuikklyService(String bluePrint) {
		this.bluePrint = bluePrint;
	}

	@Cacheable(cacheNames = "templates")
	public List<KeyValue> getTemplates() {
		JsonParser parser = new JacksonJsonParser();
		Map<String, Object> map = parser.parseMap(bluePrint);
		List<Object> templates = (List<Object>) map.get("templates");
		return templates.stream()
				.map(LinkedHashMap.class::cast)
				.map(iter -> new KeyValue(iter.get("identifier").toString(), iter.get("name").toString()))
				.collect(Collectors.toList());

	}

}