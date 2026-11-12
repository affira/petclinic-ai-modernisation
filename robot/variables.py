#
# Copyright 2002-2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Robot Framework variables file: every module-level UPPER_CASE name becomes a ${VARIABLE}.

BASE_URL points at the running PetClinic WAR. Override it with the PETCLINIC_BASE_URL environment
variable, or on the command line with: robot --variable BASE_URL:http://localhost:9967 ...
"""
import os

BASE_URL = os.environ.get("PETCLINIC_BASE_URL", "http://localhost:9966")
