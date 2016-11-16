/* 
 * TURNUS - www.turnus.co
 * 
 * Copyright (C) 2010-2016 EPFL SCI STI MM
 *
 * This file is part of TURNUS.
 *
 * TURNUS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TURNUS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TURNUS.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7
 * 
 * If you modify this Program, or any covered work, by linking or combining it
 * with Eclipse (or a modified version of Eclipse or an Eclipse plugin or 
 * an Eclipse library), containing parts covered by the terms of the 
 * Eclipse Public License (EPL), the licensors of this Program grant you 
 * additional permission to convey the resulting work.  Corresponding Source 
 * for a non-source form of such a combination shall include the source code 
 * for the parts of Eclipse libraries used as well as that of the  covered work.
 * 
 */
package turnus.orcc.profiler.dynamic.numa.samplesparser;

import java.math.BigInteger;

/**
 * Abstract class representing both function symbols and objects symbols.
 * 
 * @author Manuel Selva 
 *
 */
public abstract class Symbol {

	protected int tid;

	protected final BigInteger address;

	protected final int size;

	protected final String section;

	public String getSection() {
		return section;
	}

	protected final String name;

	public Symbol(int tid, BigInteger address, int size, String section, String name) {
		this.tid = tid;
		this.address = address;
		this.size = size;
		this.section = section;
		this.name = name;
	}

	public Symbol(BigInteger address, int size, String section, String name) {
		this.address = address;
		this.size = size;
		this.section = section;
		this.name = name;
	}

	public BigInteger getAddress() {
		return address;
	}

	public int getSize() {
		return size;
	}

	public String getName() {
		return name;
	}

	public boolean contains(BigInteger address) {
		if (address == this.address) {
			return true;
		} else {
			//return this.address < address && address < this.address + this.size;
			
			BigInteger sizeBigInteger = new BigInteger(Integer.toString(this.size));
			boolean firstCondition = this.address.compareTo(address) == -1 ? true : false;
			boolean secondCondition = address.compareTo(this.address.add(sizeBigInteger)) == -1 ? true : false;
			
			return firstCondition && secondCondition;
		}
	}
}
