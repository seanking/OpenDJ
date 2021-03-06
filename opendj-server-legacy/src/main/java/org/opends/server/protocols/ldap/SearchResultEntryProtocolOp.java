/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Base64;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.SearchResultEntry;

/**
 * This class defines the structures and methods for an LDAP search result entry
 * protocol op, which is used to return entries that match the associated search
 * criteria.
 */
public class SearchResultEntryProtocolOp
       extends ProtocolOp
{
  /** The set of attributes for this search entry. */
  private LinkedList<LDAPAttribute> attributes;

  /** The DN for this search entry. */
  private final DN dn;

  /** The underlying search result entry. */
  private SearchResultEntry entry;

  /** The LDAP version (determines how attribute options are handled). */
  private final int ldapVersion;



  /**
   * Creates a new LDAP search result entry protocol op with the specified DN
   * and no attributes.
   *
   * @param  dn  The DN for this search result entry.
   */
  public SearchResultEntryProtocolOp(DN dn)
  {
    this(dn, null, null, 3);
  }



  /**
   * Creates a new LDAP search result entry protocol op with the specified DN
   * and set of attributes.
   *
   * @param  dn          The DN for this search result entry.
   * @param  attributes  The set of attributes for this search result entry.
   */
  public SearchResultEntryProtocolOp(DN dn,
                                     LinkedList<LDAPAttribute> attributes)
  {
    this(dn, attributes, null, 3);
  }



  /**
   * Creates a new search result entry protocol op from the provided search
   * result entry.
   *
   * @param  searchEntry  The search result entry object to use to create this
   *                      search result entry protocol op.
   */
  public SearchResultEntryProtocolOp(SearchResultEntry searchEntry)
  {
    this(searchEntry.getName(), null, searchEntry, 3);
  }



  /**
   * Creates a new search result entry protocol op from the provided search
   * result entry and ldap protocol version.
   *
   * @param  searchEntry  The search result entry object to use to create this
   *                      search result entry protocol op.
   * @param ldapVersion The version of the LDAP protocol.
   */
  public SearchResultEntryProtocolOp(SearchResultEntry searchEntry,
          int ldapVersion)
  {
    this(searchEntry.getName(), null, searchEntry, ldapVersion);
  }



  /** Generic constructor. */
  private SearchResultEntryProtocolOp(DN dn,
      LinkedList<LDAPAttribute> attributes, SearchResultEntry searchEntry,
      int ldapVersion)
  {
    this.dn = dn;
    this.attributes = attributes;
    this.entry = searchEntry;
    this.ldapVersion = ldapVersion;
  }



  /**
   * Retrieves the DN for this search result entry.
   *
   * @return  The DN for this search result entry.
   */
  public DN getDN()
  {
    return dn;
  }


  /**
   * Retrieves the set of attributes for this search result entry.  The returned
   * list may be altered by the caller.
   *
   * @return  The set of attributes for this search result entry.
   */
  public LinkedList<LDAPAttribute> getAttributes()
  {
    LinkedList<LDAPAttribute> tmp = attributes;
    if (tmp == null)
    {
      tmp = new LinkedList<>();
      if (entry != null)
      {
        if (ldapVersion == 2)
        {
          // Merge attributes having the same type into a single attribute.
          merge(tmp, entry.getUserAttributes());
          merge(tmp, entry.getOperationalAttributes());
        }
        else
        {
          // LDAPv3
          for (Attribute a : entry.getAllAttributes())
          {
            tmp.add(new LDAPAttribute(a));
          }
        }
      }

      attributes = tmp;

      // Since the attributes are mutable, null out the entry for consistency.
      entry = null;
    }
    return attributes;
  }

  private void merge(LinkedList<LDAPAttribute> tmp, Map<AttributeType, List<Attribute>> attrs)
  {
    boolean needsMerge;
    for (Map.Entry<AttributeType, List<Attribute>> attrList : attrs.entrySet())
    {
      needsMerge = true;

      if (attrList != null && attrList.getValue().size() == 1)
      {
        Attribute a = attrList.getValue().get(0);
        if (!a.getAttributeDescription().hasOptions())
        {
          needsMerge = false;
          tmp.add(new LDAPAttribute(a));
        }
      }

      if (needsMerge)
      {
        AttributeBuilder builder =
            new AttributeBuilder(attrList.getKey());
        for (Attribute a : attrList.getValue())
        {
          builder.addAll(a);
        }
        tmp.add(new LDAPAttribute(builder.toAttribute()));
      }
    }
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  @Override
  public byte getType()
  {
    return OP_TYPE_SEARCH_RESULT_ENTRY;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  @Override
  public String getProtocolOpName()
  {
    return "Search Result Entry";
  }



  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
    stream.writeOctetString(dn.toString());

    stream.writeStartSequence();
    SearchResultEntry tmp = entry;
    if (ldapVersion == 3 && tmp != null)
    {
      for (List<Attribute> attrList : tmp.getUserAttributes()
          .values())
      {
        for (Attribute a : attrList)
        {
          writeAttribute(stream, a);
        }
      }

      for (List<Attribute> attrList : tmp.getOperationalAttributes()
          .values())
      {
        for (Attribute a : attrList)
        {
          writeAttribute(stream, a);
        }
      }
    }
    else
    {
      for (LDAPAttribute attr : getAttributes())
      {
        attr.write(stream);
      }
    }
    stream.writeEndSequence();

    stream.writeEndSequence();
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("SearchResultEntry(dn=");
    buffer.append(dn);
    buffer.append(", attrs={");

    LinkedList<LDAPAttribute> tmp = getAttributes();
    if (! tmp.isEmpty())
    {
      Iterator<LDAPAttribute> iterator = tmp.iterator();
      iterator.next().toString(buffer);

      while (iterator.hasNext())
      {
        buffer.append(", ");
        iterator.next().toString(buffer);
      }
    }

    buffer.append("})");
  }



  /**
   * Appends a multi-line string representation of this LDAP protocol op to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  @Override
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Search Result Entry");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  DN:  ");
    buffer.append(dn);
    buffer.append(EOL);

    buffer.append("  Attributes:");
    buffer.append(EOL);

    for (LDAPAttribute attribute : getAttributes())
    {
      attribute.toString(buffer, indent+4);
    }
  }



  /**
   * Appends an LDIF representation of the entry to the provided buffer.
   *
   * @param  buffer      The buffer to which the entry should be appended.
   * @param  wrapColumn  The column at which long lines should be wrapped.
   */
  public void toLDIF(StringBuilder buffer, int wrapColumn)
  {
    // Add the DN to the buffer.
    String dnString = dn.toString();
    int    colsRemaining;
    if (needsBase64Encoding(dnString))
    {
      dnString = Base64.encode(getBytes(dnString));
      buffer.append("dn:: ");

      colsRemaining = wrapColumn - 5;
    }
    else
    {
      buffer.append("dn: ");

      colsRemaining = wrapColumn - 4;
    }

    int dnLength = dnString.length();
    if (dnLength <= colsRemaining || colsRemaining <= 0)
    {
      buffer.append(dnString);
      buffer.append(EOL);
    }
    else
    {
      buffer.append(dnString, 0, colsRemaining);
      buffer.append(EOL);

      int startPos = colsRemaining;
      while (dnLength - startPos > wrapColumn - 1)
      {
        buffer.append(" ");
        buffer.append(dnString, startPos, startPos+wrapColumn-1);
        buffer.append(EOL);

        startPos += wrapColumn-1;
      }

      if (startPos < dnLength)
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos));
        buffer.append(EOL);
      }
    }


    // Add the attributes to the buffer.
    for (LDAPAttribute a : getAttributes())
    {
      String name       = a.getAttributeType();
      int    nameLength = name.length();

      for (ByteString v : a.getValues())
      {
        String valueString;
        if (needsBase64Encoding(v))
        {
          valueString = Base64.encode(v);
          buffer.append(name);
          buffer.append(":: ");

          colsRemaining = wrapColumn - nameLength - 3;
        }
        else
        {
          valueString = v.toString();
          buffer.append(name);
          buffer.append(": ");

          colsRemaining = wrapColumn - nameLength - 2;
        }

        int valueLength = valueString.length();
        if (valueLength <= colsRemaining || colsRemaining <= 0)
        {
          buffer.append(valueString);
          buffer.append(EOL);
        }
        else
        {
          buffer.append(valueString, 0, colsRemaining);
          buffer.append(EOL);

          int startPos = colsRemaining;
          while (valueLength - startPos > wrapColumn - 1)
          {
            buffer.append(" ");
            buffer.append(valueString, startPos, startPos+wrapColumn-1);
            buffer.append(EOL);

            startPos += wrapColumn-1;
          }

          if (startPos < valueLength)
          {
            buffer.append(" ");
            buffer.append(valueString.substring(startPos));
            buffer.append(EOL);
          }
        }
      }
    }


    // Make sure to add an extra blank line to ensure that there will be one
    // between this entry and the next.
    buffer.append(EOL);
  }



  /**
   * Converts this protocol op to a search result entry.
   *
   * @return  The search result entry created from this protocol op.
   *
   * @throws  LDAPException  If a problem occurs while trying to create the
   *                         search result entry.
   */
  public SearchResultEntry toSearchResultEntry()
         throws LDAPException
  {
    if (entry != null)
    {
      return entry;
    }

    HashMap<ObjectClass,String> objectClasses = new HashMap<>();
    HashMap<AttributeType,List<Attribute>> userAttributes = new HashMap<>();
    HashMap<AttributeType,List<Attribute>> operationalAttributes = new HashMap<>();


    for (LDAPAttribute a : getAttributes())
    {
      Attribute     attr     = a.toAttribute();
      AttributeDescription attrDesc = attr.getAttributeDescription();
      AttributeType attrType = attrDesc.getAttributeType();

      if (attrType.isObjectClass())
      {
        for (ByteString os : a.getValues())
        {
          String ocName = os.toString();
          ObjectClass oc = DirectoryServer.getInstance().getServerContext().getSchema().getObjectClass(ocName);
          objectClasses.put(oc, ocName);
        }
      }
      else if (attrType.isOperational())
      {
        List<Attribute> attrs = operationalAttributes.get(attrType);
        if (attrs == null)
        {
          attrs = new ArrayList<>(1);
          operationalAttributes.put(attrType, attrs);
        }
        attrs.add(attr);
      }
      else
      {
        List<Attribute> attrs = userAttributes.get(attrType);
        if (attrs == null)
        {
          attrs = newArrayList(attr);
          userAttributes.put(attrType, attrs);
        }
        else
        {
          // Check to see if any of the existing attributes in the list have the
          // same set of options.  If so, then add the values to that attribute.
          boolean attributeSeen = false;
          for (int i = 0; i < attrs.size(); i++) {
            Attribute ea = attrs.get(i);
            if (ea.getAttributeDescription().equals(attrDesc))
            {
              AttributeBuilder builder = new AttributeBuilder(ea);
              builder.addAll(attr);
              attrs.set(i, builder.toAttribute());
              attributeSeen = true;
            }
          }
          if (!attributeSeen)
          {
            // This is the first occurrence of the attribute and options.
            attrs.add(attr);
          }
        }
      }
    }

    Entry entry = new Entry(dn, objectClasses, userAttributes,
                            operationalAttributes);
    return new SearchResultEntry(entry);
  }



  /** Write an attribute without converting to an LDAPAttribute. */
  private void writeAttribute(ASN1Writer stream, Attribute a)
      throws IOException
  {
    stream.writeStartSequence();
    stream.writeOctetString(a.getAttributeDescription().toString());
    stream.writeStartSet();
    for (ByteString value : a)
    {
      stream.writeOctetString(value);
    }
    stream.writeEndSequence();
    stream.writeEndSequence();
  }
}

