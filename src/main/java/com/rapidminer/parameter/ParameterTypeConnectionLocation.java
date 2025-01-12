/**
 * Copyright (C) 2001-2019 by RapidMiner and the contributors
 *
 * Complete list of developers available at our web site:
 *
 * http://rapidminer.com
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 */
package com.rapidminer.parameter;

/**
 * Parameter type for selecting a {@link com.rapidminer.connection.ConnectionInformation} from the repository.
 * Can narrow down the type of the connection if necessary
 *
 * @author Jan Czogalla
 * @since 9.3
 */
public class ParameterTypeConnectionLocation extends ParameterTypeRepositoryLocation {

	private String conType;

	/**
	 * Minimal constructor, accepting the location of a
	 * {@link com.rapidminer.connection.ConnectionInformation ConnectionInformation} of any type.
	 */
	public ParameterTypeConnectionLocation(String key, String description) {
		this(key, description, null);
	}

	/** Constructor with restricting the allowed type of the connection. */
	public ParameterTypeConnectionLocation(String key, String description, String conType) {
		super(key, description, true, false, true, true);
		setExpert(false);
		this.conType = conType;
	}

	/** Get the allowed type of connections. Returns {@code null} if any connection is allowed. */
	public String getConnectionType() {
		return conType;
	}
}
