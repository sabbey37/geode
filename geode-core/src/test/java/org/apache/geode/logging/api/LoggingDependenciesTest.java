/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.logging.api;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = {"org.apache.geode.logging..", "org.apache.geode.internal.logging.."})
public class LoggingDependenciesTest {
  @ArchTest
  public static final ArchRule loggingDoesNotDependOnLog4JCore = classes()
      .that()
      .resideInAnyPackage("org.apache.geode.logging..", "org.apache.geode.internal.logging..")
      .should()
      .onlyDependOnClassesThat(not(resideInAPackage("org.apache.logging.log4j.core..")));

  @ArchTest
  public static final ArchRule loggingDoesNotDependOnGeodeLog4J = classes()
      .that()
      .resideInAnyPackage("org.apache.geode.logging..", "org.apache.geode.internal.logging..")
      .should()
      .onlyDependOnClassesThat(not(resideInAPackage("org.apache.geode.logging.log4j..")));
}
