package com.facilcomanda.ponoa;

import org.springframework.boot.SpringApplication;

public class TestPlatformFacilcomandaPonoaApiSpringApplication {

	public static void main(String[] args) {
		SpringApplication.from(PlatformFacilcomandaPonoaApiSpringApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
