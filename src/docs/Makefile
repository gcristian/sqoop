#  Licensed to Cloudera, Inc. under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  Cloudera, Inc. licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at#
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

BUILDROOT=../../build
BUILD_DIR=$(BUILDROOT)/docs

all: man userguide devguide

userguide: $(BUILD_DIR)/SqoopUserGuide.html

devguide: $(BUILD_DIR)/SqoopDevGuide.html

MANSOURCES=$(shell echo man/sqoop*.txt)
MANPAGES=$(MANSOURCES:.txt=.1.gz)

man: $(MANPAGES)

%.1.gz : %.txt
	asciidoc --unsafe -b docbook -d manpage $<
	xmlto man $*.xml -o man
	gzip $*.1
	rm $*.xml
	mkdir -p $(BUILD_DIR)
	mv $@ $(BUILD_DIR)

$(BUILD_DIR)/SqoopUserGuide.html: user/*.txt
	asciidoc -a toc -a toclevels=1 -a "toc-title=Table of Contents" \
		user/SqoopUserGuide.txt
	mkdir -p $(BUILD_DIR)
	mv user/SqoopUserGuide.html $(BUILD_DIR)

$(BUILD_DIR)/SqoopDevGuide.html: dev/*.txt
	asciidoc -a toc -a toclevels=1 -a "toc-title=Table of Contents" \
		dev/SqoopDevGuide.txt
	mkdir -p $(BUILD_DIR)
	mv dev/SqoopDevGuide.html $(BUILD_DIR)

clean:
	-rm $(BUILD_DIR)/sqoop-*.1.gz
	-rm $(BUILD_DIR)/SqoopUserGuide.html
	-rm user/SqoopUserGuide.html
	-rm dev/SqoopDevGuide.html

.PHONY: all man userguide devguide clean

