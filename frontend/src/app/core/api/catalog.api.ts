import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AppRole,
  BandResult,
  Municipality,
  Page,
  Parameter,
  ParameterHistory,
  SupplyLine,
  UserSummary,
} from '../models';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  supplyLines(): Observable<SupplyLine[]> {
    return this.http.get<SupplyLine[]>('/api/public/supply-lines');
  }

  municipalities(): Observable<Municipality[]> {
    return this.http.get<Municipality[]>('/api/public/municipalities');
  }

  band(estimatedValue: number): Observable<BandResult> {
    return this.http.get<BandResult>('/api/public/bands', {
      params: new HttpParams().set('estimatedValue', estimatedValue),
    });
  }
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  parameters(): Observable<Parameter[]> {
    return this.http.get<Parameter[]>('/api/admin/parameters');
  }

  updateParameter(key: string, value: string, reason: string): Observable<Parameter> {
    return this.http.put<Parameter>(`/api/admin/parameters/${key}`, { value, reason });
  }

  parameterHistory(key: string): Observable<ParameterHistory[]> {
    return this.http.get<ParameterHistory[]>(`/api/admin/parameters/${key}/history`);
  }

  users(q: string, role: AppRole | null, page = 0): Observable<Page<UserSummary>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (q) params = params.set('q', q);
    if (role) params = params.set('role', role);
    return this.http.get<Page<UserSummary>>('/api/admin/users', { params });
  }

  inviteUser(email: string, fullName: string, roles: AppRole[]): Observable<UserSummary> {
    return this.http.post<UserSummary>('/api/admin/users', { email, fullName, roles });
  }

  updateUser(
    u: UserSummary,
    changes: Partial<Pick<UserSummary, 'fullName' | 'roles' | 'enabled'>>,
  ) {
    return this.http.put<UserSummary>(`/api/admin/users/${u.id}`, {
      fullName: changes.fullName ?? u.fullName,
      roles: changes.roles ?? u.roles,
      enabled: changes.enabled ?? u.enabled,
      version: u.version,
    });
  }
}
